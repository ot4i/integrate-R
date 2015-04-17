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

import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbService;

/**
 * A class with utility methods for logging messages and user trace to IBM Integration Bus
 * from an instance of an R node.
 */
public class RNodeLog {
	
	private static final String R_NODE_LOG_CATALOG = "BIPmsgs";
	
	/**
	 * Log a informational message to the system log.
	 * @param object object making the call.
	 * @param methodName name of method making the call.
	 * @param messageNumber number of the message to log.
	 * @param traceText trace text to insert.
	 * @param inserts inserts for the message.
	 * @throws MbException if a problem occurs logging the message.
	 */
	public static void logInformation(Object object, String methodName, long messageNumber, String traceText, Object... inserts) throws MbException {
		MbService.logInformation(object, methodName, R_NODE_LOG_CATALOG, Long.toString(messageNumber), traceText, getObjectInserts(inserts));
	}
	
	/**
	 * Log a warning message to the system log.
	 * @param object object making the call.
	 * @param methodName name of method making the call.
	 * @param messageNumber number of the message to log.
	 * @param traceText trace text to insert.
	 * @param inserts inserts for the message.
	 * @throws MbException if a problem occurs logging the message.
	 */
	public static void logWarning(Object object, String methodName, long messageNumber, String traceText, Object... inserts) throws MbException {
		MbService.logWarning(object, methodName, R_NODE_LOG_CATALOG, Long.toString(messageNumber), traceText, getObjectInserts(inserts));
	}
	
	/**
	 * Log an error message to the system log.
	 * @param object object making the call.
	 * @param methodName name of method making the call.
	 * @param messageNumber number of the message to log.
	 * @param traceText trace text to insert.
	 * @param inserts inserts for the message.
	 * @throws MbException if a problem occurs logging the message.
	 */
	public static void logError(Object object, String methodName, long messageNumber, String traceText, Object... inserts) throws MbException {
		MbService.logError(object, methodName, R_NODE_LOG_CATALOG, Long.toString(messageNumber), traceText, getObjectInserts(inserts));
	}
	
	/**
	 * Log a message to user trace.
	 * @param object object making the call.
	 * @param methodName name of method making the call.
	 * @param messageNumber number of the message to log.
	 * @param traceText trace text to insert.
	 * @param inserts inserts for the message.
	 * @throws MbException if a problem occurs logging the message.
	 */
	public static void logUserTrace(Object object, String methodName, long messageNumber, String traceText, Object... inserts) throws MbException {
		MbService.logUserTrace(object, methodName, R_NODE_LOG_CATALOG, Long.toString(messageNumber), traceText, getStringInserts(inserts));
	}
	
	/**
	 * Log a message to debug level user trace.
	 * @param object object making the call.
	 * @param methodName name of method making the call.
	 * @param messageNumber number of the message to log.
	 * @param traceText trace text to insert.
	 * @param inserts inserts for the message.
	 * @throws MbException if a problem occurs logging the message.
	 */
	public static void logUserDebugTrace(Object object, String methodName, long messageNumber, String traceText, Object... inserts) throws MbException {	
		MbService.logUserDebugTrace(object, methodName, R_NODE_LOG_CATALOG, Long.toString(messageNumber), traceText, getStringInserts(inserts));
	}
	
	/**
	 * Convert an array of object inserts into an array of strings,
	 * replacing any MbElement objects with the element paths.
	 * @param inserts the array of object inserts.
	 * @return the converted array of strings.
	 */
	private static String[] getStringInserts(Object[] inserts) {
		String[] actualInserts = new String[inserts.length];
		for (int i = 0; i < inserts.length; i++) {
			Object insert = inserts[i];
			if (insert instanceof MbElement) {
				try {
					MbElement element = (MbElement) insert;
					String elementPath = "";
					while (element != null) {
						int previousSiblings = 0;
						MbElement sibling = element.getPreviousSibling();
						while (sibling != null) {
							if (sibling.getNamespace().equals(element.getNamespace()) && sibling.getName().equals(element.getName())) {
								previousSiblings++;
							}
							sibling = sibling.getPreviousSibling();
						}
						int totalSiblings = previousSiblings;
						try {
							sibling = element.getNextSibling();
							while (sibling != null) {
								if (sibling.getNamespace().equals(element.getNamespace()) && sibling.getName().equals(element.getName())) {
									totalSiblings++;
									break;
								}
								sibling = sibling.getNextSibling();
							}
						} catch (MbException e) {
							
						}
						if (!element.getNamespace().isEmpty()) {
							elementPath = "/{" + element.getNamespace() + "}:" + element.getName() + (totalSiblings > 0 ? "[" + (previousSiblings + 1) + "]" : "") + elementPath;
						} else {
							elementPath = "/" + element.getName() + (totalSiblings > 0 ? "[" + (previousSiblings + 1) + "]" : "") + elementPath;
						}
						element = element.getParent();
					}
					actualInserts[i] = elementPath;
				} catch (MbException e) {
					actualInserts[i] = inserts[i].toString();
				}
			} else {
				actualInserts[i] = inserts[i].toString();
			}
		}
		return actualInserts;
	}

	/**
	 * Convert an array of object inserts into an array of objects,
	 * replacing any MbElement objects with the element paths.
	 * @param inserts the array of object inserts.
	 * @return the converted array of objects.
	 */
	private static Object[] getObjectInserts(Object[] inserts) {
		Object[] actualInserts = new Object[inserts.length];
		for (int i = 0; i < inserts.length; i++) {
			Object insert = inserts[i];
			if (insert instanceof MbElement) {
				try {
					MbElement element = (MbElement) insert;
					String elementPath = "";
					while (element != null) {
						int previousSiblings = 0;
						MbElement sibling = element.getPreviousSibling();
						while (sibling != null) {
							if (sibling.getNamespace().equals(element.getNamespace()) && sibling.getName().equals(element.getName())) {
								previousSiblings++;
							}
							sibling = sibling.getPreviousSibling();
						}
						int totalSiblings = previousSiblings;
						try {
							sibling = element.getNextSibling();
							while (sibling != null) {
								if (sibling.getNamespace().equals(element.getNamespace()) && sibling.getName().equals(element.getName())) {
									totalSiblings++;
									break;
								}
								sibling = sibling.getNextSibling();
							}
						} catch (MbException e) {
							
						}
						if (!element.getNamespace().isEmpty()) {
							elementPath = "/{" + element.getNamespace() + "}:" + element.getName() + (totalSiblings > 0 ? "[" + (previousSiblings + 1) + "]" : "") + elementPath;
						} else {
							elementPath = "/" + element.getName() + (totalSiblings > 0 ? "[" + (previousSiblings + 1) + "]" : "") + elementPath;
						}
						element = element.getParent();
					}
					actualInserts[i] = elementPath;
				} catch (MbException e) {
					actualInserts[i] = inserts[i].toString();
				}
			} else {
				actualInserts[i] = inserts[i];
			}
		}
		return actualInserts;
	}
	
}
