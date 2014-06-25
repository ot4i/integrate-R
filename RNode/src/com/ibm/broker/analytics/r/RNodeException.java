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
import com.ibm.broker.plugin.MbRecoverableException;

/**
 * A class that represents a exception thrown to IBM Integration Bus from an instance
 * of an R node.
 */
public class RNodeException extends MbRecoverableException {

	private static final long serialVersionUID = -6285105471924325971L;
	
	private static final String R_NODE_EXCEPTION_CATALOG = "BIPmsgs";
	
	public RNodeException(Object object, String methodName, long messageNumber, String traceText, Object... inserts) {
		super(object, methodName, R_NODE_EXCEPTION_CATALOG, Long.toString(messageNumber), traceText, getObjectInserts(inserts));
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
