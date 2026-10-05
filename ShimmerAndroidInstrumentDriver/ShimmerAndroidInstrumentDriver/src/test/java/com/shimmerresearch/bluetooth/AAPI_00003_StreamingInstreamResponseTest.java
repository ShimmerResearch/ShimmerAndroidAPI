package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Handler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.comms.wiredProtocol.ShimmerCrc;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;

/**
 * DEV-1149: while streaming, the firmware's in-stream responses come between
 * data packets: [ACK][INSTREAM_CMD_RESPONSE][response opcode][payload], then
 * the link CRC if one is on. They are the replies to GET_STATUS_COMMAND,
 * GET_VBATT_COMMAND and GET_DIR_COMMAND, and the status push the firmware
 * sends unprompted on a dock, an undock, the user button, a trial-duration
 * expiry or a low-battery stop. Shimmer.processPacket() mishandled them in
 * three ways:
 * <ol>
 * <li>It parsed a response without waiting for its link CRC, and consumed
 * nothing when the CRC had not arrived, so the next read parsed the response
 * again and passed the data packet before it on twice. A GET_DIR reply whose
 * name had not all arrived threw a NullPointerException.</li>
 * <li>It took every in-stream response for the reply to the queued command in
 * flight, and removed that command from the instruction queue. But while
 * streaming, readBattery() and readStatusLogAndStream() write their commands
 * straight to the socket, and a status push answers no command at all. A SET
 * command was removed while it awaited its ACK, and the ACK was then processed
 * against the next instruction: for startSDLogging(), an
 * ArrayIndexOutOfBoundsException on the IO thread.</li>
 * <li>While streaming and SD logging, it read the rest of the response from
 * the socket, although Shimmer had already read it into its buffer.</li>
 * </ol>
 * Each scenario is replayed with every link CRC mode, and with the bytes
 * arriving in reads of every size, from one byte to all of them at once.
 * <p>
 * No hardware: the parser reads from an in-memory byte queue. It lives in this
 * package because the parser's state is protected or package-private in
 * ShimmerBluetooth.
 */
public class AAPI_00003_StreamingInstreamResponseTest {

	/** Bytes after the DATA_PACKET byte: a 3-byte timestamp and three 2-byte channels */
	private static final int PACKET_SIZE = 9;

	/** As many bytes as any scenario here sends in one go, so the largest read takes everything at once */
	private static final int MAX_READ_SIZE = 48;

	/** The battery ADC value in vbattReply() */
	private static final int VBATT_ADC = 0x0B24;

	/** Status byte 0: an SD card is in, and the device is streaming and has its clock set */
	private static final byte STATUS_STREAMING = 0x20|0x10|0x04|0x02;

	/** Status byte 0: as STATUS_STREAMING, and logging to the SD card too */
	private static final byte STATUS_STREAMING_AND_LOGGING = STATUS_STREAMING|0x08;

	/** The GET_DIR_COMMAND reply: the SD file path without its file number */
	private static final String DIRECTORY_NAME = "Trial/Shim_A1B2-";

	/** A Shimmer whose Bluetooth link is in memory */
	private static class QueueShimmer extends Shimmer {
		final Deque<Byte> rx = new ArrayDeque<Byte>();
		final ByteArrayOutputStream tx = new ByteArrayOutputStream();
		int bytesSkipped = 0;

		QueueShimmer() {
			super((Handler) null, (Context) null);
			// A device has been initialised by the time it streams. Before then, a
			// status response queues SET_RWC_COMMAND to set the clock.
			mIsInitialised = true;
		}

		void queue(byte[] bytes) {
			for(byte b:bytes){
				rx.add(b);
			}
		}

		/** What the IO thread does on each pass while streaming */
		void ioThreadPass() {
			processWhileStreaming();
		}

		@Override
		protected int availableBytes() {
			return rx.size();
		}

		@Override
		protected byte[] readBytes(int numberofBytes) {
			// Shimmer's would block for bytes that have not arrived, and none will
			assertTrue("read " + numberofBytes + " bytes but only " + rx.size() + " had arrived", numberofBytes<=rx.size());
			byte[] bytes = new byte[numberofBytes];
			for(int i=0;i<numberofBytes;i++){
				bytes[i] = rx.poll();
			}
			return bytes;
		}

		@Override
		protected void writeBytes(byte[] data) {
			tx.write(data, 0, data.length);
		}

		@Override
		protected void discardFirstBufferByte() {
			bytesSkipped++;
			super.discardFirstBufferByte();
		}

		@Override
		protected void discardBufferBytesToNextPacket() {
			int sizeBefore = mByteArrayOutputStream.size();
			super.discardBufferBytesToNextPacket();
			bytesSkipped += sizeBefore-mByteArrayOutputStream.size();
		}
	}

	/** One run of a scenario, with this link CRC and the bytes arriving readSize at a time */
	private interface Scenario {
		void run(BT_CRC_MODE crcMode, int readSize) throws Exception;
	}

	private final List<QueueShimmer> mDevices = new ArrayList<QueueShimmer>();

	@After
	public void stopAckTimers() {
		for(QueueShimmer device:mDevices){
			device.stopTimerCheckForAckOrResp();
		}
	}

	/**
	 * Shimmer reads whatever has arrived, so a frame can be split across reads
	 * anywhere. Runs the scenario with each link CRC mode, and the bytes
	 * arriving one at a time, two at a time and so on, up to all of them at once.
	 */
	private static void forEveryCrcModeAndReadSize(Scenario scenario) throws Exception {
		for(BT_CRC_MODE crcMode:BT_CRC_MODE.values()){
			for(int readSize=1;readSize<=MAX_READ_SIZE;readSize++){
				String run = crcMode + ", bytes arriving " + readSize + " at a time: ";
				try {
					scenario.run(crcMode, readSize);
				}
				catch(AssertionError e){
					throw new AssertionError(run + e.getMessage(), e);
				}
				catch(RuntimeException e){
					throw new AssertionError(run + e, e);
				}
			}
		}
	}

	/** A Shimmer3R part way through streaming PACKET_SIZE-byte packets */
	private QueueShimmer streamingShimmer3r(BT_CRC_MODE crcMode) {
		QueueShimmer device = new QueueShimmer();
		mDevices.add(device);
		device.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 7));
		device.setPacketSize(PACKET_SIZE);
		device.setCurrentBtCommsCrcMode(crcMode);
		device.setBluetoothRadioState(BT_STATE.STREAMING);
		// Queue each parsed packet rather than build an ObjectCluster from it
		device.mUseProcessingThread = true;
		return device;
	}

	/** What the IO thread does when it sends the next queued instruction while streaming */
	private static void sendNextInstruction(QueueShimmer device) {
		device.mCurrentCommand = device.getListofInstructions().get(0)[0];
		device.setInstructionStackLock(true);
		device.mWaitForAck = true;
		device.startTimerCheckForAckOrResp(2);
	}

	/** Hands the frames to the streaming parser readSize bytes at a time, one IO thread pass per read */
	private static void receive(QueueShimmer device, int readSize, byte[]... frames) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		for(byte[] frame:frames){
			bytes.write(frame, 0, frame.length);
		}
		byte[] all = bytes.toByteArray();
		assertTrue("the largest read takes all " + all.length + " bytes at once", all.length<=MAX_READ_SIZE);
		for(int start=0;start<all.length;start+=readSize){
			device.queue(Arrays.copyOfRange(all, start, Math.min(start+readSize, all.length)));
			device.ioThreadPass();
		}
	}

	/** Has a 0x00 and a 0xFF in it, which a resync would take for the start of a frame */
	private static byte[] payload(int i) {
		return new byte[] {(byte) (0x10*i), 0x00, 0x01, (byte) 0xFF, 0x07, 0x3C, 0x08, 0x3F, 0x09};
	}

	private static byte[] dataPacket(byte[] payload, BT_CRC_MODE crcMode) {
		byte[] frame = new byte[1+payload.length];
		frame[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(payload, 0, frame, 1, payload.length);
		return withCrc(frame, crcMode);
	}

	/** The reply to a SET command */
	private static byte[] bareAck(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED}, crcMode);
	}

	/** [ACK][INSTREAM_CMD_RESPONSE][VBATT_RESPONSE][ADC, LSB first][charger status] */
	private static byte[] vbattReply(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE,
				ShimmerObject.VBATT_RESPONSE, 0x24, 0x0B, (byte) 0x80}, crcMode);
	}

	/**
	 * [ACK][INSTREAM_CMD_RESPONSE][STATUS_RESPONSE][status byte 0][status byte 1]:
	 * the firmware's status push, the same frame as its GET_STATUS_COMMAND reply.
	 * Shimmer3R LogAndStream sends two status bytes from v1.00.024.
	 */
	private static byte[] statusPush(byte status, BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE,
				ShimmerObject.STATUS_RESPONSE, status, 0x00}, crcMode);
	}

	/** [ACK][INSTREAM_CMD_RESPONSE][DIR_RESPONSE][name length][name] */
	private static byte[] dirReply(BT_CRC_MODE crcMode) {
		byte[] name = DIRECTORY_NAME.getBytes(StandardCharsets.US_ASCII);
		byte[] frame = new byte[4+name.length];
		frame[0] = ShimmerObject.ACK_COMMAND_PROCESSED;
		frame[1] = ShimmerObject.INSTREAM_CMD_RESPONSE;
		frame[2] = ShimmerObject.DIR_RESPONSE;
		frame[3] = (byte) name.length;
		System.arraycopy(name, 0, frame, 4, name.length);
		return withCrc(frame, crcMode);
	}

	/** The frame followed by the link CRC over all of it, low byte first */
	private static byte[] withCrc(byte[] frame, BT_CRC_MODE crcMode) {
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(frame, frame.length);
		byte[] framed = Arrays.copyOf(frame, frame.length+crcMode.getNumCrcBytes());
		System.arraycopy(crc, 0, framed, frame.length, crcMode.getNumCrcBytes());
		return framed;
	}

	private static String hex(byte[] bytes) {
		return UtilShimmer.bytesToHexStringWithSpacesFormatted(bytes);
	}

	private static void assertParsed(QueueShimmer device, byte[]... expectedPayloads) {
		List<String> expected = new ArrayList<String>();
		for(byte[] payload:expectedPayloads){
			expected.add(hex(payload));
		}
		List<String> parsed = new ArrayList<String>();
		// Package-private, so Shimmer does not inherit it
		ShimmerBluetooth parser = device;
		for(RawBytePacketWithPCTimeStamp packet:parser.mABQPacketByeArray){
			parsed.add(hex(packet.mDataArray));
		}
		assertEquals("every good data packet is parsed, once and in order", expected, parsed);
	}

	/** The commands in the instruction queue, in order */
	private static void assertQueued(QueueShimmer device, byte... expectedCommands) {
		List<String> expected = new ArrayList<String>();
		for(byte command:expectedCommands){
			expected.add(ShimmerBluetooth.btCommandToString(command));
		}
		List<String> queued = new ArrayList<String>();
		for(byte[] instruction:device.getListofInstructions()){
			queued.add(ShimmerBluetooth.btCommandToString(instruction[0]));
		}
		assertEquals("the instruction queue", expected, queued);
	}

	/** The command was sent and nothing has answered it yet */
	private static void assertAwaitingReply(QueueShimmer device, byte command) {
		assertEquals("the command in flight", ShimmerBluetooth.btCommandToString(command),
				ShimmerBluetooth.btCommandToString(device.mCurrentCommand));
		assertTrue("it still awaits its reply", device.mWaitForAck);
		assertTrue("so the next instruction cannot be sent", device.isInstructionStackLock());
		assertNotNull("and its ACK timer still runs", device.mTimerCheckForAckOrResp);
	}

	/** The command in flight was answered, and the next can be sent */
	private static void assertAnswered(QueueShimmer device) {
		assertFalse("the reply is taken", device.mWaitForAck);
		assertFalse("the next instruction can be sent", device.isInstructionStackLock());
		assertNull("the ACK timer is cancelled, so it cannot clear the instruction queue", device.mTimerCheckForAckOrResp);
	}

	/** What is left in the parser's buffer, each byte with its PC timestamp */
	private static void assertBuffered(QueueShimmer device, byte... expected) {
		assertEquals(hex(expected), hex(device.mByteArrayOutputStream.toByteArray()));
		assertEquals("one PC timestamp per buffered byte", expected.length, device.mListofPCTimeStamps.size());
	}

	/** The parser never lost its place, so every frame was consumed whole and once */
	private static void assertNoResync(QueueShimmer device) {
		assertEquals("bytes skipped to find the next frame", 0, device.bytesSkipped);
	}

	/**
	 * A response is processed once, when all of it has arrived. Split at its
	 * link CRC, it was processed again on the next read, and the data packet
	 * before it passed on twice.
	 */
	@Test
	public void inStreamResponseSplitAcrossReads() throws Exception {
		forEveryCrcModeAndReadSize((crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.readBattery();
			assertEquals("while streaming, Shimmer writes the command straight out rather than queue it",
					hex(new byte[] {ShimmerObject.GET_VBATT_COMMAND}), hex(device.tx.toByteArray()));

			receive(device, readSize, dataPacket(payload(1), crcMode), vbattReply(crcMode),
					dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});

			assertEquals(VBATT_ADC, device.getBattStatusDetails().getBattAdcValue(), 0);
			assertParsed(device, payload(1), payload(2));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}

	/**
	 * The reply to the queued command in flight completes it, once, however the
	 * reply is split. A status push that arrives first answers nothing (the
	 * protocol's rule: match a reply to its command by response opcode, never by
	 * its ACK).
	 */
	@Test
	public void replyToTheQueuedCommandInFlightCompletesItOnce() throws Exception {
		forEveryCrcModeAndReadSize((crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.readDirectoryName();
			device.startSDLogging();
			sendNextInstruction(device);

			receive(device, readSize, dataPacket(payload(1), crcMode), statusPush(STATUS_STREAMING, crcMode),
					dataPacket(payload(2), crcMode));
			assertAwaitingReply(device, ShimmerObject.GET_DIR_COMMAND);
			assertQueued(device, ShimmerObject.GET_DIR_COMMAND, ShimmerObject.SET_RWC_COMMAND, ShimmerObject.START_LOGGING_ONLY_COMMAND);

			receive(device, readSize, dataPacket(payload(3), crcMode), dirReply(crcMode),
					dataPacket(payload(4), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertEquals(DIRECTORY_NAME, device.getDirectoryName());
			assertAnswered(device);
			assertQueued(device, ShimmerObject.SET_RWC_COMMAND, ShimmerObject.START_LOGGING_ONLY_COMMAND);

			assertParsed(device, payload(1), payload(2), payload(3), payload(4));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}

	/**
	 * startSDLogging() while streaming queues SET_RWC_COMMAND, then
	 * START_LOGGING_ONLY_COMMAND. In-stream responses that answer neither leave
	 * both alone: the battery timer's reading, which readBattery() wrote straight
	 * out, while the first awaits its ACK, and a status push while the second
	 * waits its turn. The first used to take SET_RWC_COMMAND off the queue, so
	 * its ACK was processed against START_LOGGING_ONLY_COMMAND's instruction.
	 */
	@Test
	public void responsesThatAnswerNoQueuedCommandLeaveTheQueueAlone() throws Exception {
		forEveryCrcModeAndReadSize((crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.startSDLogging();
			sendNextInstruction(device);
			device.readBattery();

			receive(device, readSize, dataPacket(payload(1), crcMode), vbattReply(crcMode), dataPacket(payload(2), crcMode));
			assertEquals(VBATT_ADC, device.getBattStatusDetails().getBattAdcValue(), 0);
			assertAwaitingReply(device, ShimmerObject.SET_RWC_COMMAND);
			assertQueued(device, ShimmerObject.SET_RWC_COMMAND, ShimmerObject.START_LOGGING_ONLY_COMMAND);

			receive(device, readSize, dataPacket(payload(3), crcMode), bareAck(crcMode), dataPacket(payload(4), crcMode));
			assertAnswered(device);
			assertQueued(device, ShimmerObject.START_LOGGING_ONLY_COMMAND);

			receive(device, readSize, dataPacket(payload(5), crcMode), statusPush(STATUS_STREAMING, crcMode), dataPacket(payload(6), crcMode));
			assertQueued(device, ShimmerObject.START_LOGGING_ONLY_COMMAND);

			sendNextInstruction(device);
			receive(device, readSize, dataPacket(payload(7), crcMode), bareAck(crcMode),
					dataPacket(payload(8), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertAnswered(device);
			assertQueued(device);
			assertTrue(device.isSDLogging());
			assertEquals(BT_STATE.STREAMING_AND_SDLOGGING, device.getBluetoothRadioState());

			assertParsed(device, payload(1), payload(2), payload(3), payload(4), payload(5), payload(6), payload(7), payload(8));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}

	/**
	 * While streaming and SD logging, as while only streaming, a response is
	 * parsed from what Shimmer has already read. It used to be read from the
	 * socket, as the driver does: QueueShimmer.readBytes() fails such a read,
	 * where Shimmer's would wait for later bytes and take them for the response.
	 */
	@Test
	public void inStreamResponseWhileStreamingAndSdLogging() throws Exception {
		forEveryCrcModeAndReadSize((crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);

			// The user button starts logging, so the firmware pushes its status
			receive(device, readSize, dataPacket(payload(1), crcMode), statusPush(STATUS_STREAMING_AND_LOGGING, crcMode),
					dataPacket(payload(2), crcMode));
			assertTrue(device.isSDLogging());
			assertEquals(BT_STATE.STREAMING_AND_SDLOGGING, device.getBluetoothRadioState());

			device.readBattery();
			receive(device, readSize, dataPacket(payload(3), crcMode), vbattReply(crcMode),
					dataPacket(payload(4), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertEquals(VBATT_ADC, device.getBattStatusDetails().getBattAdcValue(), 0);
			assertEquals(BT_STATE.STREAMING_AND_SDLOGGING, device.getBluetoothRadioState());

			assertParsed(device, payload(1), payload(2), payload(3), payload(4));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}
}
