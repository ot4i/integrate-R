/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and other Contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM - initial implementation
 *******************************************************************************/

package com.ibm.broker.analytics.r;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.xml.bind.DatatypeConverter;

/**
 * A class that can load the contents of an R script from the file system and
 * check for updates to that file.
 */
public class RNodeScript {
	
	/**
	 * The R node that owns this R script.
	 */
	private RNode iOwner;
	
	/**
	 * The file name of this R script.
	 */
	private String iFileName;
	
	/**
	 * The file system path of this R script.
	 */
	private Path iPath;
	
	/**
	 * The last modified time for the currently loaded contents of this R script.
	 */
	private FileTime iLastModified;
	
	/**
	 * The currently loaded contents of this R script.
	 */
	private String[] iContent;
	
	/**
	 * A unique key for this R script - safe for use in R variable names.
	 */
	private String iKey;
	
	/**
	 * The currently loaded version of this R script.
	 */
	private long iVersion = 0;
	
	/**
	 * A read-write lock to ensure that the file contents can be replaced safely.
	 */
	private ReadWriteLock iReadWriteLock = new ReentrantReadWriteLock();
	
	/**
	 * Constructor - load the specified R script from the file system.
	 * @param owner the R node that owns this R script.
	 * @param fileName the file name of this R script.
	 * @throws RNodeException if the R script cannot be loaded.
	 */
	public RNodeScript(RNode owner, String fileName) throws RNodeException {
		final String methodName = "RNodeScript";
		try {
			
			// Save the owner, file name and path.
			iOwner = owner;
			iFileName = fileName;
			iPath = Paths.get(iFileName);
			
			// Get the last modified time of the file before we load it.
			iLastModified = Files.getLastModifiedTime(iPath);
			
			// Load the file line by line into a string array.
			List<String> content = Files.readAllLines(iPath, StandardCharsets.UTF_8);
			iContent = content.toArray(new String[0]);
			
			// Get the SHA-1 digest for the file name to use as a unique key for this R script.
			MessageDigest sha1digest = MessageDigest.getInstance("SHA-1");
			iKey = DatatypeConverter.printHexBinary(sha1digest.digest(iFileName.getBytes(StandardCharsets.UTF_8))).toLowerCase();
			
		} catch (NoSuchAlgorithmException nsae) {
		} catch (IOException ioe) {
			throw new RNodeException(this, methodName, 7804, "Specified file could not open or could not be accessed", iOwner.getName(), fileName);
		}
	}
	
	/**
	 * Get the file name of this R script.
	 * @return the file name of this R script.
	 */
	public String getFileName() {
		return iFileName;
	}
	
	/**
	 * Get the currently loaded contents of this R script.
	 * @return the currently loaded contents of this R script.
	 */
	public String[] getContent() {
		return iContent;
	}
	
	/**
	 * Get a unique key for this R script - safe for use in R variable names.
	 * @return a unique key for this R script.
	 */
	public String getKey() {
		return iKey;
	}
	
	/**
	 * Get the currently loaded version of this R script.
	 * @return the currently loaded version of this R script.
	 */
	public long getVersion() {
		return iVersion;
	}
	
	/**
	 * Get a read lock to ensure that the file contents can be replaced safely.
	 */
	public Lock getReadLock() {
		return iReadWriteLock.readLock();
	}

	/**
	 * Check to see if this R script has been updated on the file system, and if so
	 * reload the contents from the updated R script.
	 * @throws RNodeException if the R script cannot be loaded.
	 */
	public void update() throws RNodeException {
		final String methodName = "update";
		try {
			
			// Compare the current last modified time with our saved last modified time.
			FileTime lastModified = Files.getLastModifiedTime(iPath);
			if (lastModified.compareTo(iLastModified) > 0) {
				
				// Ensure we're the only ones able to reload the contents.
				Lock writeLock = iReadWriteLock.writeLock();
				writeLock.lock();
				try {
					
					// Check to see if somebody else updated the file first.
					if (lastModified.compareTo(iLastModified) > 0) {
						
						// Load the updated file contents from the file system.
						iLastModified = Files.getLastModifiedTime(iPath);
						List<String> content = Files.readAllLines(iPath, StandardCharsets.UTF_8);
						iContent = content.toArray(new String[0]);
						
						// Increment the version number to force it to be reparsed.
						iVersion++;
						
					}
				
				} finally {
					writeLock.unlock();
				}
				
			}
			
		} catch (IOException ioe) {
			throw new RNodeException(this, methodName, 7804, "Specified file could not open or could not be accessed", iOwner.getName(), iFileName);
		}
	}
	
}
