package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Handler;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * DEV-1431, while streaming: firmware that cannot start a log refuses
 * START_LOGGING_ONLY_COMMAND in the middle of a stream as it does when idle
 * (docked or on USB power, no SD card or a bad one, logging turned off in its
 * configuration). Its NACK comes between two data packets, followed by its own
 * link CRC when the link has one (C5, or C5 56).
 * <p>
 * Shimmer.processPacket() had no branch for a NACK: it threw the NACK away a
 * byte at a time with the packet in front of it, and 2 s later the driver's
 * ACK timer took the device for logging. Now the packet is kept, the NACK's
 * CRC is checked and consumed, and the command is failed by the driver's
 * processNackFromCommand(), as the driver's own parser does
 * (Shimmer-Java-Android-API API_00037_StreamingLogStartRefusedTest, which this
 * ports): the device is not taken to be logging, the stream goes on, and
 * eventLogStartRefused() is called.
 * <p>
 * Shimmer reads whatever has arrived, so each scenario is replayed with the
 * bytes arriving in reads of every size, from one byte to all of them at once.
 * No hardware: the parser reads from an in-memory byte queue.
 */
public class AAPI_00009_StreamingLogStartRefusedTest {

	/** Bytes after the DATA_PACKET byte: a 3-byte timestamp and three 2-byte channels */
	private static final int PACKET_SIZE = 9;

	/** More than any scenario here sends in one go, so the largest read takes everything at once */
	private static final int MAX_READ_SIZE = 48;

	/** A Shimmer whose Bluetooth link is in memory, recording the refusals it is told of */
	private static class QueueShimmer extends Shimmer {
		final Deque<Byte> rx = new ArrayDeque<Byte>();
		final List<Byte> logStartsRefused = Collections.synchronizedList(new ArrayList<Byte>());
		final List<Byte> commandsRefused = Collections.synchronizedList(new ArrayList<Byte>());
		int bytesSkipped = 0;

		QueueShimmer() {
			super((Handler) null, (Context) null);
		}

		void queue(byte[] bytes) {
			for(byte b:bytes){
				rx.add(b);
			}
		}

		/** What the IO thread does on each pass while streaming (protected in Shimmer's package) */
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
		}

		@Override
		protected void discardFirstBufferByte() {
			bytesSkipped++;
			super.discardFirstBufferByte();
		}

		@Override
		protected void eventLogStartRefused(byte command) {
			logStartsRefused.add(command);
			super.eventLogStartRefused(command);
		}

		@Override
		protected void eventCommandRefused(byte command) {
			commandsRefused.add(command);
		}
	}

	/** One run of a scenario, with the bytes arriving readSize at a time */
	private interface Scenario {
		void run(int readSize) throws Exception;
	}

	private final List<QueueShimmer> mDevices = new ArrayList<QueueShimmer>();

	@After
	public void stopAckTimers() {
		for(QueueShimmer device:mDevices){
			device.stopTimerCheckForAckOrResp();
		}
	}

	private static void forEveryReadSize(Scenario scenario) throws Exception {
		for(int readSize=1;readSize<=MAX_READ_SIZE;readSize++){
			try {
				scenario.run(readSize);
			}
			catch(AssertionError e){
				throw new AssertionError("bytes arriving " + readSize + " at a time: " + e.getMessage(), e);
			}
		}
	}

	/** A Shimmer3R on LogAndStream v1.01.025 part way through streaming PACKET_SIZE-byte packets */
	private QueueShimmer streamingShimmer3r(BT_CRC_MODE crcMode) {
		QueueShimmer device = new QueueShimmer();
		mDevices.add(device);
		device.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25));
		device.setPacketSize(PACKET_SIZE);
		device.setCurrentBtCommsCrcMode(crcMode);
		device.setIsStreaming(true);
		device.setBluetoothRadioState(BT_STATE.STREAMING);
		// Queue each parsed packet rather than build an ObjectCluster from it
		device.mUseProcessingThread = true;
		return device;
	}

	/** startSDLogging() while streaming, and the IO thread sending its START_LOGGING_ONLY */
	private static void startSdLoggingWhileStreaming(QueueShimmer device) {
		device.startSDLogging();
		assertEquals("only START_LOGGING_ONLY_COMMAND is queued", 1, device.getListofInstructions().size());
		device.mCurrentCommand = device.getListofInstructions().get(0)[0];
		assertEquals(ShimmerObject.START_LOGGING_ONLY_COMMAND, device.mCurrentCommand);
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
		for(int start=0;start<all.length;start+=readSize){
			device.queue(Arrays.copyOfRange(all, start, Math.min(start+readSize, all.length)));
			device.ioThreadPass();
		}
	}

	/** Has a 0x00, a 0xFE and a 0xFF in it, which a resync would take for the start of a frame */
	private static byte[] payload(int i) {
		return new byte[] {(byte) (0x10*i), 0x00, 0x01, (byte) 0xFF, 0x07, (byte) 0xFE, 0x08, 0x3F, 0x09};
	}

	private static byte[] dataPacket(byte[] payload, BT_CRC_MODE crcMode) {
		byte[] frame = new byte[1+payload.length];
		frame[0] = ShimmerObject.DATA_PACKET;
		System.arraycopy(payload, 0, frame, 1, payload.length);
		return withCrc(frame, crcMode);
	}

	/** The firmware's refusal, with its link CRC */
	private static byte[] bareNack(BT_CRC_MODE crcMode) {
		return withCrc(new byte[] {ShimmerObject.NACK_COMMAND_PROCESSED}, crcMode);
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

	/** What is left in the parser's buffer, each byte with its PC timestamp */
	private static void assertBuffered(QueueShimmer device, byte... expected) {
		assertEquals(hex(expected), hex(device.mByteArrayOutputStream.toByteArray()));
		assertEquals("one PC timestamp per buffered byte", expected.length, device.mListofPCTimeStamps.size());
	}

	/** The start retired as refused, the device not logging and still streaming */
	private static void assertRefused(QueueShimmer device) {
		assertFalse("the NACK is taken", device.mWaitForAck);
		assertFalse("the next instruction can be sent", device.isInstructionStackLock());
		assertNull("the ACK timer is cancelled, so its special case cannot take the device for logging",
				device.mTimerCheckForAckOrResp);
		assertTrue("the refused start is retired", device.getListofInstructions().isEmpty());
		assertFalse("the device is not taken to be logging", device.isSDLogging());
		assertTrue("the stream goes on", device.isStreaming());
		assertEquals(BT_STATE.STREAMING, device.getBluetoothRadioState());
		assertEquals("eventLogStartRefused, once", Collections.singletonList(ShimmerObject.START_LOGGING_ONLY_COMMAND),
				device.logStartsRefused);
		assertEquals("eventCommandRefused, once", Collections.singletonList(ShimmerObject.START_LOGGING_ONLY_COMMAND),
				device.commandsRefused);
	}

	private void refusedBetweenTwoPackets(BT_CRC_MODE crcMode, int readSize) {
		QueueShimmer device = streamingShimmer3r(crcMode);
		startSdLoggingWhileStreaming(device);
		receive(device, readSize, dataPacket(payload(1), crcMode), bareNack(crcMode),
				dataPacket(payload(2), crcMode), dataPacket(payload(3), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertRefused(device);
		assertParsed(device, payload(1), payload(2), payload(3));
		assertBuffered(device, ShimmerObject.DATA_PACKET);
		assertEquals("bytes skipped to find the next frame", 0, device.bytesSkipped);
	}

	@Test
	public void aLogStartRefusedMidStreamWithoutACrc() throws Exception {
		forEveryReadSize(readSize -> refusedBetweenTwoPackets(BT_CRC_MODE.OFF, readSize));
	}

	@Test
	public void aLogStartRefusedMidStreamWithAOneByteCrc() throws Exception {
		forEveryReadSize(readSize -> refusedBetweenTwoPackets(BT_CRC_MODE.ONE_BYTE_CRC, readSize));
	}

	@Test
	public void aLogStartRefusedMidStreamWithATwoByteCrc() throws Exception {
		forEveryReadSize(readSize -> refusedBetweenTwoPackets(BT_CRC_MODE.TWO_BYTE_CRC, readSize));
	}

	/**
	 * The NACK's own CRC shows it is a NACK, so a corrupt data packet ahead of it
	 * is dropped without losing the refusal
	 */
	private void refusedAfterACorruptPacket(BT_CRC_MODE crcMode, int indexToCorrupt, int readSize) {
		QueueShimmer device = streamingShimmer3r(crcMode);
		startSdLoggingWhileStreaming(device);
		byte[] corrupt = dataPacket(payload(1), crcMode);
		corrupt[indexToCorrupt] ^= 0x01;
		receive(device, readSize, corrupt, bareNack(crcMode), dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertRefused(device);
		assertParsed(device, payload(2));
		assertBuffered(device, ShimmerObject.DATA_PACKET);
	}

	@Test
	public void aRefusalWithAOneByteCrcAfterACorruptPacket() throws Exception {
		forEveryReadSize(readSize -> refusedAfterACorruptPacket(BT_CRC_MODE.ONE_BYTE_CRC, 1, readSize));
	}

	/** Only the packet CRC's second byte is wrong, which is checked separately */
	@Test
	public void aRefusalWithATwoByteCrcAfterACorruptPacket() throws Exception {
		forEveryReadSize(readSize -> refusedAfterACorruptPacket(BT_CRC_MODE.TWO_BYTE_CRC, 1+PACKET_SIZE+1, readSize));
	}

	/**
	 * A 0xFE after a packet whose next bytes are not a NACK's CRC is not taken
	 * for a refusal: the command still waits for its reply
	 */
	@Test
	public void aFeWhoseCrcDoesNotMatchIsNotARefusal() throws Exception {
		forEveryReadSize(readSize -> {
			BT_CRC_MODE crcMode = BT_CRC_MODE.TWO_BYTE_CRC;
			QueueShimmer device = streamingShimmer3r(crcMode);
			startSdLoggingWhileStreaming(device);
			receive(device, readSize, dataPacket(payload(1), crcMode), new byte[] {ShimmerObject.NACK_COMMAND_PROCESSED, 0x11, 0x22},
					dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertTrue("the start still waits for its reply", device.mWaitForAck);
			assertTrue("no refusal", device.logStartsRefused.isEmpty() && device.commandsRefused.isEmpty());
		});
	}

	/** A NACK with no command waiting for a reply has nothing to refuse, and the stream goes on */
	@Test
	public void aNackWithNoCommandInFlightIsIgnored() throws Exception {
		forEveryReadSize(readSize -> {
			BT_CRC_MODE crcMode = BT_CRC_MODE.ONE_BYTE_CRC;
			QueueShimmer device = streamingShimmer3r(crcMode);
			receive(device, readSize, dataPacket(payload(1), crcMode), bareNack(crcMode),
					dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertTrue("no refusal", device.logStartsRefused.isEmpty() && device.commandsRefused.isEmpty());
			assertTrue(device.isStreaming());
			assertParsed(device, payload(1), payload(2));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
		});
	}
}
