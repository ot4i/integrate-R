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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.xml.bind.DatatypeConverter;

/**
 * A class that can load the contents of an R file from the file system and
 * check for updates to that file.
 */
public class RNodeFile {
	
	/**
	 * The R node that owns this R file.
	 */
	private RNode iOwner;
	
	/**
	 * The file name of this R file.
	 */
	private String iFileName;
	
	/**
	 * The file system path of this R file.
	 */
	private Path iPath;
	
	/**
	 * The last modified time for the currently loaded contents of this R file.
	 */
	private FileTime iLastModified;
	
	/**
	 * The currently loaded contents of this R file.
	 */
	private byte[] iContent;
	
	/**
	 * A unique key for this R file - safe for use in R variable names.
	 */
	private String iKey;
	
	/**
	 * The currently loaded version of this R file.
	 */
	private long iVersion = 0;
	
	/**
	 * A read-write lock to ensure that the file contents can be replaced safely.
	 */
	private ReadWriteLock iReadWriteLock = new ReentrantReadWriteLock();
	
	/**
	 * Constructor - load the specified R file from the file system.
	 * @param owner the R node that owns this R file.
	 * @param fileName the file name of this R file.
	 * @throws RNodeException if the R file cannot be loaded.
	 */
	public RNodeFile(RNode owner, String fileName) throws RNodeException {
		final String methodName = "RNodeFile";
		try {
			
			// Save the owner, file name and path.
			iOwner = owner;
			iFileName = fileName;
			iPath = Paths.get(iFileName);
			
			// Get the last modified time of the file before we load it.
			iLastModified = Files.getLastModifiedTime(iPath);
			
			// Load the file line by line into a string array.
			iContent = Files.readAllBytes(iPath);
			
			// Get the SHA-1 digest for the file name to use as a unique key for this R file.
			MessageDigest sha1digest = MessageDigest.getInstance("SHA-1");
			iKey = DatatypeConverter.printHexBinary(sha1digest.digest(iFileName.getBytes(StandardCharsets.UTF_8))).toLowerCase();
			
		} catch (NoSuchAlgorithmException nsae) {
		} catch (IOException ioe) {
			throw new RNodeException(this, methodName, 7804, "Specified file could not open or could not be accessed", iOwner.getName(), fileName);
		}
	}
	
	/**
	 * Get the file name of this R file.
	 * @return the file name of this R file.
	 */
	public String getFileName() {
		return iFileName;
	}
	
	/**
	 * Get the currently loaded contents of this R file.
	 * @return the currently loaded contents of this R file.
	 */
	public byte[] getContent() {
		return iContent;
	}
	
	/**
	 * Get a unique key for this R file - safe for use in R variable names.
	 * @return a unique key for this R file.
	 */
	public String getKey() {
		return iKey;
	}
	
	/**
	 * Get the currently loaded version of this R file.
	 * @return the currently loaded version of this R file.
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
	 * Check to see if this R file has been updated on the file system, and if so
	 * reload the contents from the updated R file.
	 * @throws RNodeException if the R file cannot be loaded.
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
						iContent = Files.readAllBytes(iPath);
						
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
