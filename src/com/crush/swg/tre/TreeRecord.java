package com.crush.swg.tre;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

public class TreeRecord {
	public static final int size = 24;

	protected String archivePath;
	protected String name;

	protected int checksum;
	protected int compressionLevel;
	protected int deflatedSize;
	protected int inflatedSize;
	protected int nameOffset;
	protected int dataOffset;
	
	protected byte[] md5 = new byte[16];
	
	public TreeRecord(String archivePath) {
		this.archivePath = archivePath;
	}
	
	public void read(ByteBuffer buffer) {
		this.checksum = buffer.getInt();
		this.inflatedSize = buffer.getInt();
		this.dataOffset = buffer.getInt();
		this.compressionLevel = buffer.getInt();
		this.deflatedSize = buffer.getInt();
		this.nameOffset = buffer.getInt();
		
		if (compressionLevel == 0 && deflatedSize == 0) deflatedSize = inflatedSize;
	}
	
	public ByteBuffer getBytes() {
		ByteBuffer buffer = null;
		
		try {
			RandomAccessFile file = new RandomAccessFile(archivePath, "r");
			FileChannel channel = file.getChannel();
			MappedByteBuffer fileBuffer = channel.map(MapMode.READ_ONLY, dataOffset, deflatedSize);
			
			buffer = ByteBuffer.allocate(inflatedSize);
			
			TreeFile.inflate(fileBuffer, buffer, compressionLevel, deflatedSize);
			
			channel.close();
			file.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return buffer;
	}

	public int getNameOffset() {
		return this.nameOffset;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public int getChecksum() {
		return this.checksum;
	}

	public int getDeflatedSize() {
		return this.deflatedSize;
	}

	public int getDataOffset() {
		return this.dataOffset;
	}

	public int getCompressionLevel() {
		return this.compressionLevel;
	}

	public void setMD5Checksum(ByteBuffer buffer) {
		buffer.get(md5);
	}

	public String getName() {
		return name;
	}
}
