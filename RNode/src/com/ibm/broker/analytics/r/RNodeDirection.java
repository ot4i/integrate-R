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

/**
 * An enumeration listing the possible variable and data
 * frame column directions.
 */
public enum RNodeDirection {
	
	/**
	 * IN is from the input message to R.
	 */
	IN,
	
	/**
	 * OUT is from R to the output message.
	 */
	OUT,
	
	/**
	 * INOUT is from the input message to R, and
	 * then from R to the output message.
	 */
	INOUT
	
}
