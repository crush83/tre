package com.crush.swg.tre;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;
import com.crush.swg.lang.InvalidFileFormatException;
import com.crush.swg.lang.UnsupportedFileVersionException;

/**
 * <p>
 * A Tree Archive (*.tre) is an archive format seen in Star Wars Galaxies, and potentially other games. It is unknown
 * if this is an SOE proprietary format, or a common format with little, to no documentation available to the public
 * domain.
 * </p>
 * <h3>File Structure</h3>
 * <p>
 * TreeFile's contain Zlib compressed files in the following archive format.
 * <pre>
 * TreeFile:Header {
 *     INT32 fileId
 *     INT32 version
 *     INT32 totalRecords
 *     INT32 recordsOffset
 *     INT32 recordsCompressionLevel
 *     INT32 recordsDeflatedSize
 *     INT32 namesCompressionLevel
 *     INT32 namesDeflatedSize
 *     INT32 namesInflatedSize
 * }
 * 
 * TreeFile:Data {
 *     BYTE[] dataBlock
 *     BYTE[] recordBlock
 *     BYTE[] namesBlock
 *     BYTE[] checksumBlock
 * }
 * </pre>
 * The data section comes after the file header section, and can be broken down into 4 blocks:
 * <ul>
 * <li><code>dataBlock</code> - This is the raw data of a file archived within this TreeFile.</li>
 * <li><code>recordBlock</code> - This is a {@link TreeFileRecord}. It contains information about
 *     each file contained within the archive.</li>
 * <li><code>namesBlock</code> - This is a block of ASCII names for each file. It contains the entire path of the file,
 *     delimited by forward slash.</li>
 * <li><code>checksumBlock</code> - This is a block of MD5 checksums, used to validate the integrity of the data in this
 *     archive, linked to each file. This is used in the file scan portion of the SOE launcher to see if TREE data
 *     should be retrieved.
 * </ul>
 * </p>
 * <p>
 * This TreeFile class only assembles and stashes the {@link TreeFileRecord} information, with pointers to the data
 * for the referenced file. This allows the TreeFile to be parsed extremely quickly, a directory to be created, and
 * for individual files to be retrieved from the relevant archive when desired.
 * </p>
 */
@SuppressWarnings("deprecation")
public class TreeFile {
	protected final String filePath;

	protected int recordsCompressionLevel;
	protected int recordsDeflatedSize;
	protected int namesCompressionLevel;
	protected int namesDeflatedSize;
	protected int namesInflatedSize;
	
	public static final int TREE = 0x54524545; //'TREE'
	public static final int V005 = 0x30303035; //'0005'
	public static final int V006 = 0x30303036; //'0006'
	
	public TreeFile(final String filePath) {
		this.filePath = filePath;
		
		this.recordsCompressionLevel = 0;
		this.namesCompressionLevel = 0;
	}
	
	public void unpack(final TreeArchive archive) throws IOException {
		final RandomAccessFile file = new RandomAccessFile(filePath, "r");
		
		final FileChannel channel = file.getChannel();
		final MappedByteBuffer buffer = (MappedByteBuffer) channel.map(
				FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.LITTLE_ENDIAN);
		
		channel.close();
		file.close();
		
		final int fileId = buffer.getInt();
		
		if (fileId != TREE)
			throw new InvalidFileFormatException();
		
		final int version = buffer.getInt();
		
		if (version != V005 && version != V006)
			throw new UnsupportedFileVersionException();
		
		final int totalRecords = buffer.getInt();
		final int recordsOffset = buffer.getInt();
		
		recordsCompressionLevel = buffer.getInt();
		recordsDeflatedSize = buffer.getInt();
		namesCompressionLevel = buffer.getInt();
		namesDeflatedSize = buffer.getInt();
		namesInflatedSize = buffer.getInt();

		buffer.position(recordsOffset);
		
		final ByteBuffer recordData   = ByteBuffer.allocate(TreeRecord.size * totalRecords);

		final ByteBuffer namesData    = ByteBuffer.allocate(namesInflatedSize);
		
		inflate(buffer, recordData, recordsCompressionLevel, recordsDeflatedSize);
		inflate(buffer, namesData, namesCompressionLevel, namesDeflatedSize);
		
		final ByteBuffer checksumData = buffer.slice();

		for (int i = 0; i < totalRecords; ++i) {
			final TreeRecord record = new TreeRecord(filePath);
			record.read(recordData.order(ByteOrder.LITTLE_ENDIAN));
			
			//Find the end of the string.
			final StringBuilder stringBuilder = new StringBuilder();

			namesData.position(record.getNameOffset());
			
			byte b = 0;
			while ((b = namesData.get()) != 0)
				stringBuilder.append((char) b);
			
			record.setName(stringBuilder.toString());
			record.setMD5Checksum(checksumData);
			
			archive.add(record);
		}
	}
	
	public static void inflate(ByteBuffer buffer, ByteBuffer dst, int compressionLevel, int deflatedSize) {
		byte[] src = new byte[deflatedSize];
		buffer.get(src);
		
		//TODO can we optimize this somehow?
		if (compressionLevel == 0) {
			dst.put(src);
			dst.rewind();
			return;
		}
		
		ZStream zstream = new ZStream();
		zstream.avail_in = 0;
		zstream.inflateInit();
		zstream.next_in = src;
		zstream.next_in_index = 0;
		zstream.avail_in = src.length;
		zstream.next_out = dst.array();
		zstream.next_out_index = 0;
		zstream.avail_out = dst.array().length;
		zstream.inflate(JZlib.Z_FINISH);
		zstream.inflateEnd();
	}
}

