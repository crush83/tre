package com.crush.swg.tre;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TreeArchive is a collection of {@link TreeRecord} objects, similar to {@link TreeFile}. The difference is that a
 * {@link TreeArchive} loads multiple {@link TreeFile} objects, and adds their contents to an internal map. Since this map
 * does not allow duplicates, only the first {@link TreeRecord} encountered will be loaded. That means it is important to
 * load the tree files in newest to oldest order.
 * <p>
 * For example, imagine that you have two tree files, tree_file1.tre and tree_file2.tre, and each
 * contained the same file datatables/badge/badge_map.iff. If you load tree_file1.tre before
 * tree_file2.tre, then the badge_map from tree_file1.tre would be the one kept in the archive. The other would
 * be ignored.
 * </p>
 */
public class TreeArchive {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private final Map<String, TreeRecord> records = new ConcurrentHashMap<String, TreeRecord>();
	
	public void processLiveFile(final String filePath) throws IOException {
		final InputStream inputStream = new FileInputStream(filePath);
		final String path = filePath.substring(0, filePath.lastIndexOf(File.separator) + 1);

		try {
			Ini ini = new Ini(inputStream);
			Ini.Section section = ini.get("SharedFile");
	
			int maxSearchPriority = section.get("maxSearchPriority", int.class);
			
			String[] files;
			for (int i = 0; i < maxSearchPriority; ++i) {
				files = section.getAll("searchTree_00_" + i, String[].class);
				
				for (int k = 0; k < files.length; ++k)
					processTreeFile(path + files[k]);
	
				files = section.getAll("searchTree_01_" + i, String[].class); //Get sku1 files at the 01 position.
	
				for (int k = 0; k < files.length; ++k)
					processTreeFile(path + files[k]);
			}
		} catch (InvalidFileFormatException e) {
			logger.error(e.getMessage());
		} finally {
			inputStream.close();
		}
	}
	
	public void processTreeFile(final String filePath) throws IOException {
		final TreeFile file = new TreeFile(filePath);
		file.unpack(this);
	}
	
	public void add(final TreeRecord record) {
		records.put(record.getName(), record);
	}
	
	public TreeRecord get(final String recordPath) {
		return records.get(recordPath);
	}
	
	public List<String> getFileListInDirectory(final String directory) {
		final List<String> files = new ArrayList<String>();
		
		for (Entry<String, TreeRecord> entry : records.entrySet()) {
			if (entry.getKey().startsWith(directory))
				files.add(entry.getKey());
		}
		
		return files;
	}
	
	public void export(final String directory, final String filter) {
		try {
			long s = System.nanoTime();
			int total = records.size();
			int n = 0;
			for (Entry<String, TreeRecord> entry : records.entrySet()) {
				final String filepath = directory + entry.getKey().replace("/", File.separator);
				logger.info(String.format("[%d/%d] Writing %s", ++n, total, filepath));
				
				if (!filter.isEmpty() && !filepath.contains(filter))
					continue;
	
				final TreeRecord record = entry.getValue();
				
				File file = new File(filepath);
				
				if (file.exists())
					continue;
				
				file.getParentFile().mkdirs();
				
				RandomAccessFile raf = new RandomAccessFile(file, "rw");
				FileChannel channel = raf.getChannel();
				
				channel.write(record.getBytes());
				
				channel.close();
				raf.close();
			}
			
			logger.info("Completed in " + ((System.nanoTime() - s) / 1e9) + " seconds.");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static final void main(String[] args) throws Exception {	
		if (args.length < 2) {
			System.out.println("Usage: export.jar <source> <destination>");
			System.out.println(" source       The path to the swg_live.cfg file.");
			System.out.println(" destination  The directory path to which you want to export the files.");
			return;
		}
		
		String sourcePath = args[0];
		String targetPath = args[1];
		
		System.out.println(sourcePath);
		System.out.println(targetPath);
		
		TreeArchive archive = new TreeArchive();
		archive.processLiveFile(sourcePath);
		archive.export(targetPath, "");
	}
}
