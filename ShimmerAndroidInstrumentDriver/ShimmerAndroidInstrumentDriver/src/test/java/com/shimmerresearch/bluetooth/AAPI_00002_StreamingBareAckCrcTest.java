package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Handler;

import java.io.ByteArrayOutputStream;
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
 * DEV-1148: with a Bluetooth link CRC on, the firmware appends it to every
 * reply, a bare ACK included, so a SET command sent while streaming is
 * answered FF F4 65, or FF F4 with a 1-byte CRC: F4 65 is the CRC of the
 * single byte 0xFF. Shimmer.processPacket() took the byte after a streaming
 * ACK to be the next packet's DATA_PACKET or an in-stream response's 0x8A, so
 * F4 was a parse error. It threw away the data packet before the ACK a byte at
 * a time and never processed the ACK, and the ACK timer then cleared the
 * instruction queue. For startSDLogging() that meant START_LOGGING_ONLY_COMMAND,
 * queued behind SET_RWC_COMMAND, was never sent.
 * <p>
 * The driver fixed this in ShimmerBluetooth (DEV-1147, whose
 * API_00027_StreamingBareAckCrcTest this ports), but Shimmer overrides that
 * parser with its own. Shimmer's parser also reads every byte that has arrived
 * rather than one at a time, so each scenario here is replayed with the bytes
 * arriving in reads of every size, from one byte to all of them at once.
 * <p>
 * No hardware: the parser reads from an in-memory byte queue. It lives in this
 * package because the parser's state is protected or package-private in
 * ShimmerBluetooth.
 */
public class AAPI_00002_StreamingBareAckCrcTest {

	/** Bytes after the DATA_PACKET byte: a 3-byte timestamp and three 2-byte channels */
	private static final int PACKET_SIZE = 9;

	/** More than any scenario here sends in one go, so the largest read takes everything at once */
	private static final int MAX_READ_SIZE = 48;

	/** A Shimmer whose Bluetooth link is in memory */
	private static class QueueShimmer extends Shimmer {
		final Deque<Byte> rx = new ArrayDeque<Byte>();
		final ByteArrayOutputStream tx = new ByteArrayOutputStream();
		int bytesSkipped = 0;

		QueueShimmer() {
			super((Handler) null, (Context) null);
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

		/** Parses what is already in the buffer */
		void parseBuffered() {
			processPacket();
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

	/**
	 * Shimmer reads whatever has arrived, so a frame can be split across reads
	 * anywhere. Runs the scenario with the bytes arriving one at a time, two at
	 * a time and so on, up to all of them at once.
	 */
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
		for(int start=0;start<all.length;start+=readSize){
			device.queue(Arrays.copyOfRange(all, start, Math.min(start+readSize, all.length)));
			device.ioThreadPass();
		}
	}

	/** Hands the frames to the streaming parser one whole frame per IO thread pass */
	private static void receiveFramePerRead(QueueShimmer device, byte[]... frames) {
		for(byte[] frame:frames){
			device.queue(frame);
			device.ioThreadPass();
		}
	}

	/** Has a 0x00 and a 0xFF in it, which a resync would take for the start of a frame */
	private static byte[] payload(int i) {
		return new byte[] {(byte) (0x10*i), 0x00, 0x01, (byte) 0xFF, 0x07, 0x3C, 0x08, 0x3F, 0x09};
	}

	/** Neither this nor its CRC has a 0x00 or a 0xFF in it */
	private static byte[] payloadWithoutFrameStarts(int i) {
		return new byte[] {(byte) (0x10+i), 0x20, 0x01, 0x11, 0x07, 0x3C, 0x08, 0x3F, 0x09};
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

	private static void assertAckProcessed(QueueShimmer device) {
		assertFalse("the ACK is taken", device.mWaitForAck);
		assertFalse("the next instruction can be sent", device.isInstructionStackLock());
		assertNull("the ACK timer is cancelled, so it cannot clear the instruction queue", device.mTimerCheckForAckOrResp);
	}

	/** What is left in the parser's buffer, each byte with its PC timestamp */
	private static void assertBuffered(QueueShimmer device, byte... expected) {
		assertEquals(hex(expected), hex(device.mByteArrayOutputStream.toByteArray()));
		assertEquals("one PC timestamp per buffered byte", expected.length, device.mListofPCTimeStamps.size());
	}

	/** The parser never lost its place, so every frame, a bare ACK's CRC included, was consumed whole and once */
	private static void assertNoResync(QueueShimmer device) {
		assertEquals("bytes skipped to find the next frame", 0, device.bytesSkipped);
	}

	/**
	 * startSDLogging() while a Shimmer3R streams sends START_LOGGING_ONLY_COMMAND,
	 * with no clock write before it (DEV-1337), and the firmware answers with a
	 * bare ACK
	 */
	private void startSdLoggingWhileStreaming(BT_CRC_MODE crcMode, int readSize) {
		QueueShimmer device = streamingShimmer3r(crcMode);
		device.startSDLogging();
		assertEquals("only START_LOGGING_ONLY_COMMAND is queued", 1, device.getListofInstructions().size());

		sendNextInstruction(device);
		assertEquals(ShimmerObject.START_LOGGING_ONLY_COMMAND, device.mCurrentCommand);
		receive(device, readSize, dataPacket(payload(1), crcMode), bareAck(crcMode),
				dataPacket(payload(2), crcMode), dataPacket(payload(3), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertAckProcessed(device);
		assertTrue(device.getListofInstructions().isEmpty());
		assertTrue(device.isSDLogging());
		assertEquals(BT_STATE.STREAMING_AND_SDLOGGING, device.getBluetoothRadioState());

		assertParsed(device, payload(1), payload(2), payload(3));
		assertBuffered(device, ShimmerObject.DATA_PACKET);
		assertNoResync(device);
	}

	@Test
	public void bareAckWithOneByteCrcWhileStreaming() throws Exception {
		forEveryReadSize(readSize -> startSdLoggingWhileStreaming(BT_CRC_MODE.ONE_BYTE_CRC, readSize));
	}

	@Test
	public void bareAckWithTwoByteCrcWhileStreaming() throws Exception {
		forEveryReadSize(readSize -> startSdLoggingWhileStreaming(BT_CRC_MODE.TWO_BYTE_CRC, readSize));
	}

	/** Already worked: with no CRC the ACK is followed by the next packet's DATA_PACKET */
	@Test
	public void bareAckWithoutCrcWhileStreaming() throws Exception {
		forEveryReadSize(readSize -> startSdLoggingWhileStreaming(BT_CRC_MODE.OFF, readSize));
	}

	/**
	 * Already worked: an in-stream response's 0x8A follows the ACK, and its CRC
	 * comes at the end. Each frame arrives whole here, in a read of its own or
	 * all in one. AAPI_00003_StreamingInstreamResponseTest splits in-stream
	 * responses across reads.
	 */
	private void inStreamResponseWhileStreaming(BT_CRC_MODE crcMode, boolean framePerRead) {
		QueueShimmer device = streamingShimmer3r(crcMode);
		device.readBattery();
		assertEquals("while streaming, Shimmer writes the command straight out rather than queue it",
				hex(new byte[] {ShimmerObject.GET_VBATT_COMMAND}), hex(device.tx.toByteArray()));
		// [ACK][INSTREAM_CMD_RESPONSE][VBATT_RESPONSE][ADC, LSB first][charger status][CRC]
		byte[] vbatt = withCrc(new byte[] {ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE,
				ShimmerObject.VBATT_RESPONSE, 0x24, 0x0B, (byte) 0x80}, crcMode);
		byte[][] frames = {dataPacket(payload(1), crcMode), vbatt, dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET}};
		if(framePerRead){
			receiveFramePerRead(device, frames);
		}
		else {
			receive(device, MAX_READ_SIZE, frames);
		}
		assertEquals(0x0B24, device.getBattStatusDetails().getBattAdcValue(), 0);
		assertParsed(device, payload(1), payload(2));
		assertBuffered(device, ShimmerObject.DATA_PACKET);
		assertNoResync(device);
	}

	@Test
	public void inStreamResponseWithOneByteCrcWhileStreaming() {
		inStreamResponseWhileStreaming(BT_CRC_MODE.ONE_BYTE_CRC, true);
		inStreamResponseWhileStreaming(BT_CRC_MODE.ONE_BYTE_CRC, false);
	}

	@Test
	public void inStreamResponseWithTwoByteCrcWhileStreaming() {
		inStreamResponseWhileStreaming(BT_CRC_MODE.TWO_BYTE_CRC, true);
		inStreamResponseWhileStreaming(BT_CRC_MODE.TWO_BYTE_CRC, false);
	}

	/**
	 * The ACK's own CRC shows it is an ACK, so a corrupt data packet ahead of it
	 * is dropped without losing the ACK
	 */
	private void bareAckAfterACorruptPacket(BT_CRC_MODE crcMode, int indexToCorrupt, int readSize) {
		QueueShimmer device = streamingShimmer3r(crcMode);
		device.stopSDLogging();
		sendNextInstruction(device);
		byte[] corrupt = dataPacket(payload(1), crcMode);
		corrupt[indexToCorrupt] ^= 0x01;
		receive(device, readSize, corrupt, bareAck(crcMode), dataPacket(payload(2), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
		assertAckProcessed(device);
		assertTrue(device.getListofInstructions().isEmpty());
		assertParsed(device, payload(2));
		assertBuffered(device, ShimmerObject.DATA_PACKET);
		assertNoResync(device);
	}

	@Test
	public void bareAckWithOneByteCrcAfterACorruptPacket() throws Exception {
		forEveryReadSize(readSize -> bareAckAfterACorruptPacket(BT_CRC_MODE.ONE_BYTE_CRC, 1, readSize));
	}

	/** Only the CRC's second byte is wrong, which is checked separately */
	@Test
	public void bareAckWithTwoByteCrcAfterACorruptPacket() throws Exception {
		forEveryReadSize(readSize -> bareAckAfterACorruptPacket(BT_CRC_MODE.TWO_BYTE_CRC, 1+PACKET_SIZE+1, readSize));
	}

	/**
	 * A read can bring bytes beyond the ACK with it. Exactly the packet, the ACK
	 * and its CRC are consumed, and the rest stay with their timestamps as the
	 * start of the next frame.
	 */
	private void bareAckWithTheNextFrameAlreadyBuffered(BT_CRC_MODE crcMode) throws Exception {
		QueueShimmer device = streamingShimmer3r(crcMode);
		device.stopSDLogging();
		sendNextInstruction(device);
		byte[] nextFrameStart = Arrays.copyOf(dataPacket(payload(2), crcMode), 4);
		ByteArrayOutputStream buffered = new ByteArrayOutputStream();
		buffered.write(dataPacket(payload(1), crcMode));
		buffered.write(bareAck(crcMode));
		buffered.write(nextFrameStart);
		device.mByteArrayOutputStream.write(buffered.toByteArray());
		for(long timestamp=0;timestamp<buffered.size();timestamp++){
			device.mListofPCTimeStamps.add(timestamp);
		}

		device.parseBuffered();

		assertAckProcessed(device);
		assertParsed(device, payload(1));
		assertBuffered(device, nextFrameStart);
		assertEquals("the timestamps kept are those of the bytes kept",
				Long.valueOf(buffered.size()-nextFrameStart.length), device.mListofPCTimeStamps.get(0));
	}

	@Test
	public void bareAckWithOneByteCrcWithTheNextFrameAlreadyBuffered() throws Exception {
		bareAckWithTheNextFrameAlreadyBuffered(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void bareAckWithTwoByteCrcWithTheNextFrameAlreadyBuffered() throws Exception {
		bareAckWithTheNextFrameAlreadyBuffered(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	/**
	 * A packet whose 2-byte CRC fails on its second byte is dropped, and only it.
	 * The driver's checkCrc() skipped to the next frame itself, and
	 * processPacket() then skipped a byte more, the start of the next packet.
	 */
	@Test
	public void twoByteCrcFailingOnItsSecondByteDropsOnlyThatPacket() throws Exception {
		forEveryReadSize(readSize -> {
			BT_CRC_MODE crcMode = BT_CRC_MODE.TWO_BYTE_CRC;
			QueueShimmer device = streamingShimmer3r(crcMode);
			byte[] corrupt = dataPacket(payloadWithoutFrameStarts(1), crcMode);
			corrupt[corrupt.length-1] ^= 0x01;
			receive(device, readSize, corrupt, dataPacket(payloadWithoutFrameStarts(2), crcMode),
					dataPacket(payloadWithoutFrameStarts(3), crcMode), new byte[] {ShimmerObject.DATA_PACKET});
			assertParsed(device, payloadWithoutFrameStarts(2), payloadWithoutFrameStarts(3));
			assertBuffered(device, ShimmerObject.DATA_PACKET);
		});
	}
}
