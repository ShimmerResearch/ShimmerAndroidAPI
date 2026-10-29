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
 * DEV-1291: after STOP_LOGGING_ONLY_COMMAND the unit stops sending data packets
 * for about 100 ms while it closes its SD file. A command sent in that pause is
 * answered at once, so its ACK comes straight after the previous ACK, with no
 * data packet before it. Shimmer.processPacket() only looks for an ACK after a
 * data packet, so it threw the reply away a byte at a time. For a queued
 * command, the ACK timer then cleared the instruction queue. Both a Shimmer3
 * and a Shimmer3R do it (found by the Java HIL, DEV-1269).
 * <p>
 * The driver fixed this in ShimmerBluetooth (whose
 * API_00032_StreamingAckAfterPauseTest this ports), but Shimmer overrides that
 * parser with its own. Shimmer also writes readBattery() and
 * readStatusLogAndStream() straight to the socket while streaming rather than
 * queue them, so they are sent at once, in the pause too, and with or without a
 * link CRC.
 * <p>
 * Each scenario is replayed with the bytes arriving in reads of every size, from
 * one byte to all of them at once. No hardware: the parser reads from an
 * in-memory byte queue. It lives in this package because the parser's state is
 * protected or package-private in ShimmerBluetooth.
 */
public class AAPI_00005_StreamingAckAfterPauseTest {

	/** Bytes after the DATA_PACKET byte: a 3-byte timestamp and three 2-byte channels */
	private static final int PACKET_SIZE = 9;

	/** As many bytes as any scenario here sends in one go, so the largest read takes everything at once */
	private static final int MAX_READ_SIZE = 48;

	/** The battery ADC value in vbattReply() */
	private static final int VBATT_ADC = 0x0B24;

	/** Status byte 0: docked, an SD card in, and streaming with the clock set */
	private static final byte STATUS_DOCKED_AND_STREAMING = 0x20|0x10|0x04|0x02|0x01;

	/** The GET_DIR_COMMAND reply: the SD file path without its file number */
	private static final String DIRECTORY_NAME = "Trial/Shim_A1B2-";

	/** The link CRC modes on which a queued command can be sent in the pause */
	private static final BT_CRC_MODE[] CRC_ON = {BT_CRC_MODE.ONE_BYTE_CRC, BT_CRC_MODE.TWO_BYTE_CRC};

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
	 * anywhere. Runs the scenario with each of the link CRC modes, and the bytes
	 * arriving one at a time, two at a time and so on, up to all of them at once.
	 */
	private static void forEachCrcModeAndReadSize(BT_CRC_MODE[] crcModes, Scenario scenario) throws Exception {
		for(BT_CRC_MODE crcMode:crcModes){
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

	/**
	 * Sends STOP_LOGGING_ONLY_COMMAND, at the head of the queue, and receives its
	 * ACK after a data packet. The unit then pauses its data packets while it
	 * closes its SD file. Without a link CRC, nothing yet shows the ACK is a bare
	 * one, so it is processed when the next frame starts.
	 */
	private static void stopLoggingAndReceiveItsAck(QueueShimmer device, BT_CRC_MODE crcMode, int readSize) {
		assertQueuedFirst(device, ShimmerObject.STOP_LOGGING_ONLY_COMMAND);
		sendNextInstruction(device);
		receive(device, readSize, dataPacket(payload(1), crcMode), bareAck(crcMode));
		if(crcMode!=BT_CRC_MODE.OFF){
			assertAnswered(device);
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
	 * the GET_STATUS_COMMAND reply, the same frame as the firmware's status push.
	 * Shimmer3R LogAndStream sends two status bytes from v1.00.024.
	 */
	private static byte[] statusReply(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE,
				ShimmerObject.STATUS_RESPONSE, STATUS_DOCKED_AND_STREAMING, 0x00}, crcMode);
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

	private static void assertQueuedFirst(QueueShimmer device, byte command) {
		assertEquals(ShimmerBluetooth.btCommandToString(command),
				ShimmerBluetooth.btCommandToString(device.getListofInstructions().get(0)[0]));
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
	 * readStatusLogAndStream() in the pause. Its reply was thrown away, and with
	 * no link CRC the ACK of STOP_LOGGING_ONLY_COMMAND before it too.
	 */
	@Test
	public void statusReplyInThePause() throws Exception {
		forEachCrcModeAndReadSize(BT_CRC_MODE.values(), (crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.stopSDLogging();
			stopLoggingAndReceiveItsAck(device, crcMode, readSize);

			device.readStatusLogAndStream();
			assertEquals("while streaming, Shimmer writes the command straight out rather than queue it",
					hex(new byte[] {ShimmerObject.GET_STATUS_COMMAND}), hex(device.tx.toByteArray()));
			receive(device, readSize, statusReply(crcMode), dataPacket(payload(2), crcMode),
					dataPacket(payload(3), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertAnswered(device);
			assertQueued(device);
			assertTrue("the status reply is decoded", device.isDocked());

			assertParsed(device, payload(1), payload(2), payload(3));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}

	/** readBattery() in the pause */
	@Test
	public void batteryReplyInThePause() throws Exception {
		forEachCrcModeAndReadSize(BT_CRC_MODE.values(), (crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.stopSDLogging();
			stopLoggingAndReceiveItsAck(device, crcMode, readSize);

			device.readBattery();
			receive(device, readSize, vbattReply(crcMode), dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertAnswered(device);
			assertQueued(device);
			assertEquals(VBATT_ADC, device.getBattStatusDetails().getBattAdcValue(), 0);

			assertParsed(device, payload(1), payload(2));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}

	/**
	 * A queued SET command sent in the pause: its bare ACK must complete it, or
	 * the ACK timer clears the queue behind it. Only with a link CRC: without
	 * one, the ACK before it isn't processed until the stream resumes, so it is
	 * not sent in the pause.
	 */
	@Test
	public void bareAckInThePause() throws Exception {
		forEachCrcModeAndReadSize(CRC_ON, (crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.stopSDLogging();
			device.startSDLogging(); // START_LOGGING_ONLY_COMMAND: no clock write to a streaming Shimmer3R (DEV-1337)
			device.readDirectoryName();
			assertQueued(device, ShimmerObject.STOP_LOGGING_ONLY_COMMAND, ShimmerObject.START_LOGGING_ONLY_COMMAND, ShimmerObject.GET_DIR_COMMAND);
			stopLoggingAndReceiveItsAck(device, crcMode, readSize);

			sendNextInstruction(device);
			assertEquals(ShimmerObject.START_LOGGING_ONLY_COMMAND, device.mCurrentCommand);
			receive(device, readSize, bareAck(crcMode), dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertAnswered(device);
			assertQueued(device, ShimmerObject.GET_DIR_COMMAND);

			assertParsed(device, payload(1), payload(2));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}

	/** A queued GET command sent in the pause: its in-stream reply must complete it */
	@Test
	public void queuedCommandsReplyInThePause() throws Exception {
		forEachCrcModeAndReadSize(CRC_ON, (crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.stopSDLogging();
			device.readDirectoryName();
			stopLoggingAndReceiveItsAck(device, crcMode, readSize);

			sendNextInstruction(device);
			assertEquals(ShimmerObject.GET_DIR_COMMAND, device.mCurrentCommand);
			receive(device, readSize, dirReply(crcMode), dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertEquals(DIRECTORY_NAME, device.getDirectoryName());
			assertAnswered(device);
			assertQueued(device);

			assertParsed(device, payload(1), payload(2));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}

	/**
	 * Not only in the pause: a queued command's bare ACK comes straight after the
	 * reply to a read that readBattery() wrote straight out, with no data packet
	 * between them. The HIL met it on a Shimmer3 with no link CRC, where only the
	 * data packet after the ACK shows it is one.
	 */
	@Test
	public void bareAckStraightAfterAnUnqueuedReadsReply() throws Exception {
		forEachCrcModeAndReadSize(BT_CRC_MODE.values(), (crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.stopSDLogging();
			sendNextInstruction(device);
			device.readBattery();

			receive(device, readSize, dataPacket(payload(1), crcMode), vbattReply(crcMode), bareAck(crcMode),
					dataPacket(payload(2), crcMode), dataPacket(payload(3), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertAnswered(device);
			assertQueued(device);
			assertEquals(VBATT_ADC, device.getBattStatusDetails().getBattAdcValue(), 0);

			assertParsed(device, payload(1), payload(2), payload(3));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
			assertNoResync(device);
		});
	}

	/**
	 * An ACK at the start of the buffer is taken only for a reply that is due. A
	 * status push there completes neither GET_DIR_COMMAND, queued and in flight,
	 * nor the battery read that readBattery() wrote straight out.
	 */
	@Test
	public void aReplyThatIsNotDueIsNotTaken() throws Exception {
		forEachCrcModeAndReadSize(CRC_ON, (crcMode, readSize) -> {
			QueueShimmer device = streamingShimmer3r(crcMode);
			device.stopSDLogging();
			device.readDirectoryName();
			stopLoggingAndReceiveItsAck(device, crcMode, readSize);
			sendNextInstruction(device);
			device.readBattery();

			receive(device, readSize, statusReply(crcMode), dataPacket(payload(2), crcMode), dataPacket(payload(3), crcMode));
			assertAwaitingReply(device, ShimmerObject.GET_DIR_COMMAND);
			assertQueued(device, ShimmerObject.GET_DIR_COMMAND);
			assertFalse("the status push is not decoded", device.isDocked());
			assertEquals("nor taken for the battery reply", 0, device.getBattStatusDetails().getBattAdcValue(), 0);
		});
	}
}
