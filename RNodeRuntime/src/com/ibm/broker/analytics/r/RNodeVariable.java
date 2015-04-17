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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.xml.bind.DatatypeConverter;

import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPLogical;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbXPath;
import com.ibm.broker.plugin.MbXPathVariables;

/**
 * A class that represents a variable configured on an instance of an R node.
 * The variable can either be for passing data to R (IN), getting data back from
 * R (OUT) or both (INOUT).
 */
public class RNodeVariable {

	/**
	 * The R node that owns this variable.
	 */
	private RNode iOwner;
	
	/**
	 * The name of this variable and the R variable name.
	 */
	private String iName;
	
	/**
	 * The R variable type of this variable.
	 */
	private RNodeType iType;
	
	/**
	 * The direction of this variable.
	 */
	private RNodeDirection iDirection;
	
	/**
	 * The XPath expression for this variable.
	 */
	private String iXPathExpression;
	
	/**
	 * The compiled XPath expression for this variable.
	 */
	private MbXPath iXPath = null;
	
	/**
	 * Constructor.
	 * @param owner the R node that owns this variable.
	 * @param dataFrame the optional data frame this variable belongs to.
	 * @param name the name of this variable and the R variable name.
	 * @param type the R variable type of this variable.
	 * @param direction the direction of this variable.
	 * @param xpathExpression the XPath expression for this variable.
	 * @param xpath the compiled XPath expression for this variable.
	 */
	public RNodeVariable(RNode owner, String name, RNodeType type, RNodeDirection direction, String xpathExpression, MbXPath xpath) {
		iOwner = owner;
		iName = name;
		iType = type;
		iDirection = direction;
		iXPathExpression = xpathExpression;
		iXPath = xpath;
	}
	
	/**
	 * Get the name of this variable and the R variable name.
	 * @return the name of this variable and the R variable name.
	 */
	public String getName() {
		return iName;
	}
	
	/**
	 * Get the R variable type of this variable.
	 * @return the R variable type of this variable.
	 */
	public RNodeType getType() {
		return iType;
	}
	
	/**
	 * Get the direction of this variable.
	 * @return the direction of this variable.
	 */
	public RNodeDirection getDirection() {
		return iDirection;
	}
	
	/**
	 * Get the XPath expression for this variable.
	 * @return the XPath expression for this variable.
	 */
	public String getXPathExpression() {
		return iXPathExpression;
	}
	
	/**
	 * Get the compiled XPath expression for this variable.
	 * @return the compiled XPath expression for this variable.
	 */
	public MbXPath getXPath() {
		return iXPath;
	}
	
	/**
	 * Using the specified connection to an Rserve server and input message, resolve the tree elements specified by the
	 * XPath expression for this variable and convert them into R variables on the Rserve server. 
	 * @param connection the Rserve server connection to use.
	 * @param message the input message.
	 * @param xpathVariables the set of XPath variables to use.
	 * @return
	 * @throws RNodeException if a known problem occurs during processing.
	 * @throws MbException if a problem occurs during logging or accessing the input message.
	 * @throws RserveException if an unknown problem occurs interacting with the Rserve server.
	 */
	@SuppressWarnings("unchecked")
	public void toR(RConnection connection, MbMessage message, MbXPathVariables xpathVariables) throws RNodeException, MbException, RserveException {
		final String methodName = "toR";
		
		// Evaluate the XPath expression.
		Object xpathValue = message.evaluateXPath(iXPath, xpathVariables);
		Object[] xpathValues;
		List<MbElement> nodeset = null;
		
		// If the result is a nodeset, ensure it has at least one element and retrieve all the values of all the nodes.
		if (xpathValue instanceof List<?>) {
			nodeset = (List<MbElement>) xpathValue;
			if (nodeset.size() == 0) {
				throw new RNodeException(this, methodName, 7809, "XPath result is empty nodeset", iName, iOwner.getName(), iXPathExpression);
			}
			xpathValues = new Object[nodeset.size()];
			for (int i = 0; i < nodeset.size(); i++) {
				xpathValues[i] = nodeset.get(i).getValue();
			}
		} else {
			xpathValues = new Object[] { xpathValue };
		}
		
		// Convert all of the values returned from the XPath expression into the R variable type set on this variable.
		REXP value;
		switch (iType) {
		case LOGICAL: {
			byte[] actualValues = new byte[xpathValues.length];
			for (int i = 0; i < xpathValues.length; i++) {
				Object actualValue = xpathValues[i];
				if (actualValue instanceof Boolean) {
					actualValues[i] = ((Boolean) actualValue).booleanValue() ? REXPLogical.TRUE : REXPLogical.FALSE;
					RNodeLog.logUserTrace(this, methodName, 7817, "Assigning R logical value", iOwner.getName(), actualValue.toString(), iName, i + 1, xpathValues.length);
				} else {
					// Attempt to automatically cast from string to boolean.
					if (actualValue instanceof String) {
						String strValue = (String) actualValue;
						try {
							actualValues[i] = DatatypeConverter.parseBoolean(strValue) ? REXPLogical.TRUE : REXPLogical.FALSE;
							RNodeLog.logUserTrace(this, methodName, 7817, "Assigning R logical value", iOwner.getName(), strValue, iName, i + 1, xpathValues.length);
							continue;
						} catch (Exception e) {
							// Fall through to exceptions below.
						}
					} else if (actualValue == null) {
						actualValues[i] = REXPLogical.NA;
						continue;
					}
					if (nodeset != null) {
						throw new RNodeException(this, methodName, 7828, "Could not convert element value to R logical variable", iName, iOwner.getName(), nodeset.get(i));
					} else {
						throw new RNodeException(this, methodName, 7805, "Could not convert XPath result to R logical variable", iName, iOwner.getName(), iXPathExpression);
					}
				}
			}
			value = new REXPLogical(actualValues);
			break;
		}
		case INTEGER: {
			int[] actualValues = new int[xpathValues.length];
			for (int i = 0; i < xpathValues.length; i++) {
				Object actualValue = xpathValues[i];
				if (actualValue instanceof Boolean) {
					actualValues[i] = ((Boolean) actualValue).booleanValue() ? 1 : 0;
					RNodeLog.logUserTrace(this, methodName, 7818, "Assigning R integer value", iOwner.getName(), actualValue.toString(), iName, i + 1, xpathValues.length);
				} else if (actualValue instanceof Integer) {
					actualValues[i] = ((Integer) actualValue).intValue();
					RNodeLog.logUserTrace(this, methodName, 7818, "Assigning R integer value", iOwner.getName(), actualValue.toString(), iName, i + 1, xpathValues.length);
				} else if (actualValue instanceof Double) {
					actualValues[i] = (int) ((Double) actualValue).doubleValue();
					RNodeLog.logUserTrace(this, methodName, 7818, "Assigning R integer value", iOwner.getName(), actualValue.toString(), iName, i + 1, xpathValues.length);
				} else {
					// Attempt to automatically cast from string to integer.
					if (actualValue instanceof String) {
						String strValue = (String) actualValue;
						try {
							actualValues[i] = DatatypeConverter.parseInt(strValue);
							RNodeLog.logUserTrace(this, methodName, 7818, "Assigning R integer value", iOwner.getName(), strValue, iName, i + 1, xpathValues.length);
							continue;
						} catch (Exception e) {
							// Fall through to exceptions below.
						}
					} else if (actualValue == null) {
						actualValues[i] = REXPInteger.NA;
						continue;
					}
					if (nodeset != null) {
						throw new RNodeException(this, methodName, 7829, "Could not convert element value to R integer variable", iName, iOwner.getName(), nodeset.get(i));
					} else {
						throw new RNodeException(this, methodName, 7806, "Could not convert XPath result to R integer variable", iName, iOwner.getName(), iXPathExpression);
					}
				}
			}
			value = new REXPInteger(actualValues);
			break;
		}
		case DOUBLE: {
			double[] actualValues = new double[xpathValues.length];
			for (int i = 0; i < xpathValues.length; i++) {
				Object actualValue = xpathValues[i];
				if (actualValue instanceof Boolean) {
					actualValues[i] = ((Boolean) actualValue).booleanValue() ? 1 : 0;
					RNodeLog.logUserTrace(this, methodName, 7819, "Assigning R double value", iOwner.getName(), actualValue.toString(), iName, i + 1, xpathValues.length);
				} else if (actualValue instanceof Integer) {
					actualValues[i] = ((Integer) actualValue).intValue();
					RNodeLog.logUserTrace(this, methodName, 7819, "Assigning R double value", iOwner.getName(), actualValue.toString(), iName, i + 1, xpathValues.length);
				} else if (actualValue instanceof Double) {
					actualValues[i] = (int) ((Double) actualValue).doubleValue();
					RNodeLog.logUserTrace(this, methodName, 7819, "Assigning R double value", iOwner.getName(), actualValue.toString(), iName, i + 1, xpathValues.length);
				} else {
					// Attempt to automatically cast from string to double.
					if (actualValue instanceof String) {
						String strValue = (String) actualValue;
						try {
							actualValues[i] = DatatypeConverter.parseDouble(strValue);
							RNodeLog.logUserTrace(this, methodName, 7819, "Assigning R double value", iOwner.getName(), strValue, iName, i + 1, xpathValues.length);
							continue;
						} catch (Exception e) {
							// Fall through to exceptions below.
						}
					} else if (actualValue == null) {
						actualValues[i] = REXPDouble.NA;
						continue;
					}
					if (nodeset != null) {
						throw new RNodeException(this, methodName, 7830, "Could not convert element value to R double variable", iName, iOwner.getName(), nodeset.get(i));
					} else {
						throw new RNodeException(this, methodName, 7807, "Could not convert XPath result to R double variable", iName, iOwner.getName(), iXPathExpression);
					}
				}
			}
			value = new REXPDouble(actualValues);
			break;
		}
		case CHARACTER: {
			String[] actualValues = new String[xpathValues.length];
			for (int i = 0; i < xpathValues.length; i++) {
				Object actualValue = xpathValues[i];
				if (actualValue instanceof String) {
					actualValues[i] = (String) actualValue;
					RNodeLog.logUserTrace(this, methodName, 7820, "Assigning R character value", iOwner.getName(), actualValue.toString(), iName, i + 1, xpathValues.length);
				} else if (actualValue == null) {
					actualValues[i] = null;
					continue;
				} else if (nodeset != null) {
					throw new RNodeException(this, methodName, 7831, "Could not convert element value to R character variable", iName, iOwner.getName(), nodeset.get(i));
				} else {
					throw new RNodeException(this, methodName, 7808, "Could not convert XPath result to R character variable", iName, iOwner.getName(), iXPathExpression);
				}
			}
			value = new REXPString(actualValues);
			break;
		}
		default:
			throw new RNodeException(this, methodName, 2111, "Unrecognised R node variable type", "Unrecognised R node variable type");
		}
		
		// Assign the values to an R variable on the Rserve server.
		connection.assign(iName, value);
		
	}
	
	/**
	 * Using the specified connection to an Rserve server and output message, get the R variable for this variable
	 * and set it on all nodes returned by the XPath expression for this variable.
	 * @param connection the Rserve server connection to use.
	 * @param message the output message.
	 * @param xpathVariables the set of XPath variables to use.
	 * @return
	 * @throws RNodeException if a known problem occurs during processing.
	 * @throws MbException if a problem occurs during logging or accessing the output message.
	 * @throws RserveException if an unknown problem occurs interacting with the Rserve server.
	 */
	public void fromR(RConnection connection, MbMessage message, MbXPathVariables xpathVariables) throws RNodeException, MbException, REXPMismatchException {
		final String methodName = "fromR";
		REXP value;
		
		// Retrieve the R variable value from the Rserve server.
		try {
			value = connection.get(iName, null, true);
			if (value == null) {
				throw new RNodeException(this, methodName, 7832, "R variable does not exist", iName, iOwner.getName());
			}
		} catch (REngineException e) {
			throw new RNodeException(this, methodName, 7832, "R variable does not exist", iName, iOwner.getName());
		}
		
		// Evaluate the XPath expression and check that it returns a nodeset with at least one node. 
		Object xpathValue = message.evaluateXPath(iXPath, xpathVariables);
		if (!(xpathValue instanceof List<?>)) {
			throw new RNodeException(this, methodName, 7801, "XPath result is not a nodeset", iName, iOwner.getName(), iXPathExpression);
		}
		@SuppressWarnings("unchecked")
		List<MbElement> nodeset = (List<MbElement>) xpathValue;
		nodeset = new ArrayList<>(nodeset);
		ListIterator<MbElement> it = nodeset.listIterator();
		if (nodeset.size() == 0) {
			throw new RNodeException(this, methodName, 7802, "XPath result is an empty nodeset", iName, iOwner.getName(), iXPathExpression);
		}
		
		// Go through each value stored in the R variable and assign it to the next node in the nodeset. 
		int numValues = value.length();
		int i;
		for (i = 0; i < numValues; i++) {
			
			// Get the next node in the nodeset - if one doesn't exist, create a copy of the last element.
			MbElement element;
			if (!it.hasNext()) {
				MbElement last = it.previous();
				RNodeLog.logUserTrace(this, methodName, 7825, "Creating new element", iOwner.getName(), last);
				element = last.createElementAfter(last.getType());
				element.setName(last.getName());
				element.setNamespace(last.getNamespace());
				it.next();
				it.add(element);
			} else {
				element = it.next();
			}
			
			// Assign the value of the R variable to the node.
			if (value.isLogical()) {
				if (!value.isNA()[i]) {
					boolean actualValue = value.asBytes()[i] == REXPLogical.TRUE;
					RNodeLog.logUserTrace(this, methodName, 7821, "Assigning R logical value", iOwner.getName(), actualValue, iName, element, i + 1, numValues, i + 1);
					element.setValue(actualValue);
				} else {
					element.setValue(null);
				}
			} else if (value.isInteger()) {
				if (!value.isNA()[i]) {
					int actualValue = value.asIntegers()[i];
					RNodeLog.logUserTrace(this, methodName, 7822, "Assigning R integer value", iOwner.getName(), actualValue, iName, element, i + 1, numValues, i + 1);
					element.setValue(actualValue);
				} else {
					element.setValue(null);
				}
			} else if (value.isNumeric()) {
				if (!value.isNA()[i]) {
					double actualValue = value.asDoubles()[i];
					RNodeLog.logUserTrace(this, methodName, 7823, "Assigning R double value", iOwner.getName(), actualValue, iName, element, i + 1, numValues, i + 1);
					element.setValue(actualValue);
				} else {
					element.setValue(null);
				}
			} else if (value.isString()) {
				if (!value.isNA()[i]) {
					String actualValue = value.asStrings()[i];
					RNodeLog.logUserTrace(this, methodName, 7824, "Assigning R character value", iOwner.getName(), actualValue, iName, element, i + 1, numValues, i + 1);
					element.setValue(actualValue);
				} else {
					element.setValue(null);
				}
			} else {
				throw new RNodeException(this, methodName, 7803, "R variable type is not supported (not logical/integer/double/character)", iName, iOwner.getName());
			}
			
		}
		
		// For any remaining nodes in the nodeset that we haven't assigned values to, assign the last value
		// stored in the R variable.
		int elem = i;
		i--;
		while (it.hasNext()) {
			MbElement element = it.next();
			if (value.isLogical()) {
				if (!value.isNA()[i]) {
					boolean actualValue = ((REXPLogical) value).isTRUE()[i];
					RNodeLog.logUserTrace(this, methodName, 7821, "Assigning R logical value", iOwner.getName(), actualValue, iName, element, i + 1, numValues, elem + 1);
					element.setValue(actualValue);
				} else {
					element.setValue(null);
				}
			} else if (value.isInteger()) {
				if (!value.isNA()[i]) {
					int actualValue = value.asIntegers()[i];
					RNodeLog.logUserTrace(this, methodName, 7822, "Assigning R integer value", iOwner.getName(), actualValue, iName, element, i + 1, numValues, elem + 1);
					element.setValue(actualValue);
				} else {
					element.setValue(null);
				}
			} else if (value.isNumeric()) {
				if (!value.isNA()[i]) {
					double actualValue = value.asDoubles()[i];
					RNodeLog.logUserTrace(this, methodName, 7823, "Assigning R double value", iOwner.getName(), actualValue, iName, element, i + 1, numValues, elem + 1);
					element.setValue(actualValue);
				} else {
					element.setValue(null);
				}
			} else if (value.isString()) {
				if (!value.isNA()[i]) {
					String actualValue = value.asStrings()[i];
					RNodeLog.logUserTrace(this, methodName, 7824, "Assigning R character value", iOwner.getName(), actualValue, iName, element, i + 1, numValues, elem + 1);
					element.setValue(actualValue);
				} else {
					element.setValue(null);
				}
			} else {
				throw new RNodeException(this, methodName, 7803, "R variable type is not supported (not logical/integer/double/character)", iName, iOwner.getName());
			}
			elem++;
		}
		
	}
	
}
