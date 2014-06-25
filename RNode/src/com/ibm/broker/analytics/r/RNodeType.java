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
 * frame column types. These are equivalent to the types
 * of the variables in R.
 */
public enum RNodeType {
	
	/**
	 * The R logical or boolean type.
	 */
	LOGICAL("R_LOGICAL"),
	
	/**
	 * The R integer type.
	 */
	INTEGER("R_INTEGER"),
	
	/**
	 * The R double or numeric type.
	 */
	DOUBLE("R_DOUBLE"),
	
	/**
	 * The R character or string type.
	 */
	CHARACTER("R_CHARACTER"),
	
	/**
	 * The R data frame type.
	 */
	DATA_FRAME("R_DATA_FRAME");
	
	/**
	 * The message flow value for this enumeration value.
	 */
	private String iValue;
	
	/**
	 * Constructor.
	 * @param value the message flow value for this enumeration value.
	 */
	private RNodeType(String value) {
		iValue = value;
	}

	/**
	 * Get the enumeration value for the specified message flow value. 
	 * @param value the specified message flow value.
	 * @return the enumeration value for the specified message flow value.
	 */
	public static RNodeType fromString(String value) {
		for (RNodeType type : values()) {
			if (type.iValue.equalsIgnoreCase(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException();
	}

	/**
	 * Get the message flow value for the specified enumeration value. 
	 * @param value the specified enumeration value.
	 * @return the message flow value for the specified enumeration value.
	 */
	@Override
	public String toString() {
		return iValue;
	}
	
	
}
