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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPLogical;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbXPath;
import com.ibm.broker.plugin.MbXPathVariables;

/**
 * A class that represents a data frame configured on an instance of an R node.
 * The data frame can either be for passing data to R (IN), getting data back from
 * R (OUT) or both (INOUT). The direction data passes in is configured on a
 * per-column basis.
 */
public class RNodeDataFrame {
	
	/**
	 * The R node that owns this variable.
	 */
	private RNode iOwner;
	
	/**
	 * The name of this data frame and the R variable name.
	 */
	private String iName;
	
	/**
	 * The XPath expression for this column.
	 */
	private String iXPathExpression;
	
	/**
	 * The compiled XPath expression for this column.
	 */
	private MbXPath iXPath = null;
	
	/**
	 * The list of columns in this data frame.
	 */
	private Map<String, Column> iColumns = new HashMap<>();
	
	/**
	 * The list of IN and INOUT columns in this data frame.
	 */
	private List<Column> iInColumns = new ArrayList<>();
	
	/**
	 * The list of INOUT and OUT columns in this data frame.
	 */
	private List<Column> iOutColumns = new ArrayList<>();
	
	/**
	 * A class that represents a column configured within a data frame on an
	 * instance of an R node. The column can either be for passing data to R
	 * (IN), getting data back from R (OUT) or both (INOUT).
	 */
	public class Column {
		
		/**
		 * The name of this column and the R column name.
		 */
		private String iName;
		
		/**
		 * The R variable type of this column.
		 */
		private RNodeType iType;
		
		/**
		 * The direction of this column.
		 */
		private RNodeDirection iDirection;
		
		/**
		 * The XPath expression for this column.
		 */
		private String iXPathExpression;
		
		/**
		 * The compiled XPath expression for this column.
		 */
		private MbXPath iXPath = null;
		
		private Column(String name, RNodeType type, RNodeDirection direction, String xpathExpression, MbXPath xpath) {
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
		
	}
	
	/**
	 * Constructor.
	 * @param owner the R node that owns this data frame.
	 * @param name the name of this data frame and the R variable name.
	 * @param xpathExpression the XPath expression for this data frame.
	 * @param xpath the compiled XPath expression for this data frame.
	 */
	public RNodeDataFrame(RNode owner, String name, String xpathExpression, MbXPath xpath) {
		iOwner = owner;
		iName = name;
		iXPathExpression = xpathExpression;
		iXPath = xpath;
	}
	
	/**
	 * Get the name of this data frame and the R variable name.
	 * @return the name of this data frame and the R variable name.
	 */
	public String getName() {
		return iName;
	}
	
	/**
	 * Get the XPath expression for this data frame.
	 * @return the XPath expression for this data frame.
	 */
	public String getXPathExpression() {
		return iXPathExpression;
	}
	
	/**
	 * Get the compiled XPath expression for this data frame.
	 * @return the compiled XPath expression for this data frame.
	 */
	public MbXPath getXPath() {
		return iXPath;
	}
	
	/**
	 * Add a new column to this data frame.
	 * @param name the name of the column and the R column name.
	 * @param type the R variable type of this column.
	 * @param direction the direction of this column.
	 * @param xpathExpression the XPath expression for this column.
	 * @param xpath the compiled XPath expression for this column.
	 */
	public void addColumn(String name, RNodeType type, RNodeDirection direction, String xpathExpression, MbXPath xpath) throws RNodeException {
		final String methodName = "addColumn";
		Column column = new Column(name, type, direction, xpathExpression, xpath);
		if (!iColumns.containsKey(name)) {
			iColumns.put(name, column);
		} else {
			throw new RNodeException(this, methodName, 7871, "Two data frame columns with the same name", iOwner.getName(), name);
		}
		if (direction == RNodeDirection.IN || direction == RNodeDirection.INOUT) {
			iInColumns.add(column);
		}
		if (direction == RNodeDirection.OUT || direction == RNodeDirection.INOUT) {
			iOutColumns.add(column);
		}
	}
	
	/**
	 * Using the specified connection to an Rserve server and input message, resolve the tree elements specified by the
	 * XPath expression for this data frame and its columns and convert them into an R variable on the Rserve server. 
	 * @param connection the Rserve server connection to use.
	 * @param message the input message.
	 * @param xpathVariables the set of XPath variables to use.
	 * @throws RNodeException if a known problem occurs during processing.
	 * @throws MbException if a problem occurs during logging or accessing the input message.
	 * @throws RserveException if an unknown problem occurs interacting with the Rserve server.
	 * @throws REXPMismatchException if an unknown problem occurs interacting with the Rserve server.
	 */
	@SuppressWarnings("unchecked")
	public void toR(RConnection connection, MbMessage message, MbXPathVariables xpathVariables) throws RNodeException, MbException, RserveException, REXPMismatchException {
		final String methodName = "toR";
		
		// Evaluate the XPath expression.
		Object xpathValue = message.evaluateXPath(iXPath, xpathVariables);
		List<MbElement> nodeset = null;
		
		// If the result is a nodeset, ensure it has at least one element and retrieve all the values of all the nodes.
		if (xpathValue instanceof List<?>) {
			nodeset = (List<MbElement>) xpathValue;
			if (nodeset.size() == 0) {
				throw new RNodeException(this, methodName, 7840, "XPath result is empty nodeset", iName, iOwner.getName(), iXPathExpression);
			}
		} else {
			throw new RNodeException(this, methodName, 7841, "XPath result is not a nodeset", iName, iOwner.getName(), iXPathExpression);
		}
		
		// Build an array of the column names and values.
		String[] columnNames = new String[iInColumns.size()];
		REXP[] columnValues = new REXP[iInColumns.size()];
		
		// Loop over each column in this data frame.
		for (int i = 0; i < iInColumns.size(); i++) {
			
			// Save the column name.
			Column column = iInColumns.get(i);
			columnNames[i] = column.getName();
			
			// Generate the empty column value.
			byte[] booleanValues = null;
			int[] intValues = null;
			double[] doubleValues = null;
			String[] stringValues = null;
			switch (column.getType()) {
			case LOGICAL:
				booleanValues = new byte[nodeset.size()];
				break;
			case INTEGER:
				intValues = new int[nodeset.size()];
				break;
			case DOUBLE:
				doubleValues = new double[nodeset.size()];
				break;
			case CHARACTER:
				stringValues = new String[nodeset.size()];
				break;
			default:
				throw new RNodeException(this, methodName, 2111, "Unrecognised R node data frame column type", "Unrecognised R node data frame column type");
			}
			
			// Loop over each element in the nodeset.
			for (int j = 0; j < nodeset.size(); j++) {
				
				// Evaluate the XPath expression for this column.
				MbElement rowElement = nodeset.get(j);
				MbElement columnElement = null;
				Object columnValue = rowElement.evaluateXPath(column.getXPath(), xpathVariables);
				if (columnValue instanceof List<?>) {
					List<MbElement> columnNodeset = (List<MbElement>) columnValue;
					if (columnNodeset.size() == 1) {
						columnElement = columnNodeset.get(0);
						columnValue = columnElement.getValue();
					} else if (columnNodeset.size() > 1) {
						throw new RNodeException(this, methodName, 7842, "XPath result has more than one node", iName, iOwner.getName(), column.getXPathExpression(), column.getName(), rowElement);
					} else {
						columnValue = null;
					}
				}
				
				// Convert the element value into the expected type for this column.
				switch (column.getType()) {
				case LOGICAL:
					if (columnValue instanceof Boolean) {
						booleanValues[j] = ((Boolean) columnValue).booleanValue() ? REXPLogical.TRUE : REXPLogical.FALSE;
						RNodeLog.logUserTrace(this, methodName, 7858, "Assigning R logical value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
					} else {
						// Attempt to automatically cast from string to boolean.
						if (columnValue instanceof String) {
							String strValue = (String) columnValue;
							try {
								booleanValues[j] = DatatypeConverter.parseBoolean(strValue) ? REXPLogical.TRUE : REXPLogical.FALSE;
								RNodeLog.logUserTrace(this, methodName, 7858, "Assigning R logical value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
								continue;
							} catch (Exception e) {
								// Fall through to exceptions below.
							}
						} else if (columnValue == null) {
							booleanValues[j] = REXPLogical.NA;
							continue;
						}
						if (columnElement != null) {
							throw new RNodeException(this, methodName, 7854, "Could not convert element value to R logical variable", iName, iOwner.getName(), columnElement, column.getName());
						} else {
							throw new RNodeException(this, methodName, 7850, "Could not convert XPath result to R logical variable", iName, iOwner.getName(), iXPathExpression, column.getName());
						}
					}
					break;
				case INTEGER:
					if (columnValue instanceof Boolean) {
						intValues[j] = ((Boolean) columnValue).booleanValue() ? 1 : 0;
						RNodeLog.logUserTrace(this, methodName, 7859, "Assigning R integer value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
					} else if (columnValue instanceof Integer) {
						intValues[j] = ((Integer) columnValue).intValue();
						RNodeLog.logUserTrace(this, methodName, 7859, "Assigning R integer value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
					} else if (columnValue instanceof Double) {
						intValues[j] = (int) ((Double) columnValue).doubleValue();
						RNodeLog.logUserTrace(this, methodName, 7859, "Assigning R integer value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
					} else {
						// Attempt to automatically cast from string to integer.
						if (columnValue instanceof String) {
							String strValue = (String) columnValue;
							try {
								intValues[j] = DatatypeConverter.parseInt(strValue);
								RNodeLog.logUserTrace(this, methodName, 7859, "Assigning R integer value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
								continue;
							} catch (Exception e) {
								// Fall through to exceptions below.
							}
						} else if (columnValue == null) {
							intValues[j] = REXPInteger.NA;
							continue;
						}
						if (columnElement != null) {
							throw new RNodeException(this, methodName, 7855, "Could not convert element value to R integer variable", iName, iOwner.getName(), columnElement, column.getName());
						} else {
							throw new RNodeException(this, methodName, 7851, "Could not convert XPath result to R integer variable", iName, iOwner.getName(), iXPathExpression, column.getName());
						}
					}
					break;
				case DOUBLE:
					if (columnValue instanceof Boolean) {
						doubleValues[j] = ((Boolean) columnValue).booleanValue() ? 1 : 0;
						RNodeLog.logUserTrace(this, methodName, 7860, "Assigning R double value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
					} else if (columnValue instanceof Integer) {
						doubleValues[j] = ((Integer) columnValue).intValue();
						RNodeLog.logUserTrace(this, methodName, 7860, "Assigning R double value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
					} else if (columnValue instanceof Double) {
						doubleValues[j] = (int) ((Double) columnValue).doubleValue();
						RNodeLog.logUserTrace(this, methodName, 7860, "Assigning R double value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
					} else {
						// Attempt to automatically cast from string to double.
						if (columnValue instanceof String) {
							String strValue = (String) columnValue;
							try {
								doubleValues[j] = DatatypeConverter.parseDouble(strValue);
								RNodeLog.logUserTrace(this, methodName, 7860, "Assigning R double value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
								continue;
							} catch (Exception e) {
								// Fall through to exceptions below.
							}
						} else if (columnValue == null) {
							doubleValues[j] = REXPDouble.NA;
							continue;
						}
						if (columnElement != null) {
							throw new RNodeException(this, methodName, 7856, "Could not convert element value to R double variable", iName, iOwner.getName(), columnElement, column.getName());
						} else {
							throw new RNodeException(this, methodName, 7852, "Could not convert XPath result to R double variable", iName, iOwner.getName(), iXPathExpression, column.getName());
						}
					}
					break;
				case CHARACTER:
					if (columnValue instanceof String) {
						stringValues[j] = (String) columnValue;
						RNodeLog.logUserTrace(this, methodName, 7861, "Assigning R character value", iOwner.getName(), columnValue.toString(), column.getName(), j + 1, nodeset.size(), iName);
					} else if (columnValue == null) {
						stringValues[j] = null;
						continue;
					} else if (columnElement != null) {
						throw new RNodeException(this, methodName, 7857, "Could not convert element value to R character variable", iName, iOwner.getName(), columnElement, column.getName());
					} else {
						throw new RNodeException(this, methodName, 7853, "Could not convert XPath result to R character variable", iName, iOwner.getName(), iXPathExpression, column.getName());
					}
					break;
				default:
					throw new RNodeException(this, methodName, 2111, "Unrecognised R node data frame column type", "Unrecognised R node data frame column type");
				}
				
			}
			
			// Now the column values are known, we can generate the R value.
			switch (column.getType()) {
			case LOGICAL:
				columnValues[i] = new REXPLogical(booleanValues);
				break;
			case INTEGER:
				columnValues[i] = new REXPInteger(intValues);
				break;
			case DOUBLE:
				columnValues[i] = new REXPDouble(doubleValues);
				break;
			case CHARACTER:
				columnValues[i] = new REXPString(stringValues);
				break;
			default:
				throw new RNodeException(this, methodName, 2111, "Unrecognised R node data frame column type", "Unrecognised R node data frame column type");
			}
					
		}
		
		// Create the R data frame object.
		REXP dataFrame = REXP.createDataFrame(new RList(columnValues, columnNames));
		
		// Assign the data frame to an R variable on the Rserve server.
		connection.assign(iName, dataFrame);
		
	}
	
	/**
	 * Using the specified connection to an Rserve server and output message, get the R variable for this data frame
	 * and set it on all nodes returned by the XPath expression for this data frame and its columns.
	 * @param connection the Rserve server connection to use.
	 * @param message the output message.
	 * @param xpathVariables the set of XPath variables to use.
	 * @throws RNodeException if a known problem occurs during processing.
	 * @throws MbException if a problem occurs during logging or accessing the output message.
	 * @throws RserveException if an unknown problem occurs interacting with the Rserve server.
	 */
	@SuppressWarnings("unchecked")
	public void fromR(RConnection connection, MbMessage message, MbXPathVariables xpathVariables) throws RNodeException, MbException, REXPMismatchException {
		final String methodName = "fromR";
		
		// It is a data frame - retrieve the R data frame variable from the Rserve server.
		REXP dataFrame = null;
		try {
			dataFrame = connection.get(iName, null, true);
			if (dataFrame == null) {
				throw new RNodeException(this, methodName, 7833, "R data frame does not exist", iName, iOwner.getName());
			}
		} catch (REngineException e) {
			throw new RNodeException(this, methodName, 7833, "R data frame does not exist", iName, iOwner.getName());
		}
		
		// Build an array of the column names and values.
		REXP[] columnValues = new REXP[iOutColumns.size()];
		
		// Check that it is a data frame variable, and retrieve the required columns from the data frame.
		if (dataFrame.isList() && dataFrame.inherits("data.frame")) {
			RList contents = dataFrame.asList();
			for (int i = 0; i < iOutColumns.size(); i++) {
				Column column = iOutColumns.get(i);
				columnValues[i] = contents.at(column.getName());
				if (columnValues[i] == null) {
					throw new RNodeException(this, methodName, 7834, "R data frame does not contain the specified column", iName, iOwner.getName(), column.getName());
				}
			}
		} else {
			throw new RNodeException(this, methodName, 7835, "R variable is not a data frame", iName, iOwner.getName());
		}
		
		// Determine the expected number of rows.
		int expectedRows = columnValues[0].length();
		
		// Evaluate the XPath expression and check that it returns a nodeset with at least one node. 
		Object xpathValue = message.evaluateXPath(iXPath, xpathVariables);
		if (!(xpathValue instanceof List<?>)) {
			throw new RNodeException(this, methodName, 7845, "XPath result is not a nodeset", iName, iOwner.getName(), iXPathExpression);
		}
		List<MbElement> nodeset = (List<MbElement>) xpathValue;
		nodeset = new ArrayList<>(nodeset);
		if (nodeset.size() == 0) {
			throw new RNodeException(this, methodName, 7844, "XPath result is empty nodeset", iName, iOwner.getName(), iXPathExpression);
		}
		
		// Fill the nodeset with additional elements if required.
		while (nodeset.size() < expectedRows) {
			MbElement last = nodeset.get(nodeset.size() - 1);
			MbElement element = last.createElementAfter(last.getType());
			element.setName(last.getName());
			element.setNamespace(last.getNamespace());
			nodeset.add(element);
		}
		
		// Loop over all the elements.
		int currentRow = 0;
		for (int i = 0; i < nodeset.size(); i++) {
			
			// Loop over each column in this data frame.
			MbElement rowElement = nodeset.get(i);
			for (int j = 0; j < iOutColumns.size(); j++) {
			
				// Evaluate the XPath expression for this column.
				Column column = iOutColumns.get(j);
				MbElement columnElement = null;
				Object columnValue = rowElement.evaluateXPath(column.getXPath(), xpathVariables);
				if (columnValue instanceof List<?>) {
					List<MbElement> columnNodeset = (List<MbElement>) columnValue;
					if (columnNodeset.size() == 1) {
						columnElement = columnNodeset.get(0);
					} else if (columnNodeset.size() > 1) {
						throw new RNodeException(this, methodName, 7846, "XPath result has more than one node", iName, iOwner.getName(), column.getXPathExpression(), column.getName(), rowElement);
					} else if (columnNodeset.size() == 0) {
						throw new RNodeException(this, methodName, 7847, "XPath result is empty nodeset", iName, iOwner.getName(), column.getXPathExpression(), column.getName(), rowElement);
					}
				} else {
					throw new RNodeException(this, methodName, 7848, "XPath result is empty nodeset", iName, iOwner.getName(), column.getXPathExpression(), column.getName(), rowElement);
				}
				
				// Set the value on the element.
				REXP value = columnValues[j];
				if (value.isLogical()) {
					if (!value.isNA()[currentRow]) {
						boolean actualValue = value.asBytes()[currentRow] == REXPLogical.TRUE;
						RNodeLog.logUserTrace(this, methodName, 7862, "Assigning R logical value", iOwner.getName(), actualValue, column.getName(), columnElement, currentRow + 1, expectedRows, i + 1, iName);
						columnElement.setValue(actualValue);
					} else {
						columnElement.setValue(null);
					}
				} else if (value.isInteger()) {
					if (!value.isNA()[currentRow]) {
						int actualValue = value.asIntegers()[currentRow];
						RNodeLog.logUserTrace(this, methodName, 7863, "Assigning R integer value", iOwner.getName(), actualValue, column.getName(), columnElement, currentRow + 1, expectedRows, i + 1, iName);
						columnElement.setValue(actualValue);
					} else {
						columnElement.setValue(null);
					}
				} else if (value.isNumeric()) {
					if (!value.isNA()[currentRow]) {
						double actualValue = value.asDoubles()[currentRow];
						RNodeLog.logUserTrace(this, methodName, 7864, "Assigning R double value", iOwner.getName(), actualValue, column.getName(), columnElement, currentRow + 1, expectedRows, i + 1, iName);
						columnElement.setValue(actualValue);
					} else {
						columnElement.setValue(null);
					}
				} else if (value.isString()) {
					if (!value.isNA()[currentRow]) {
						String actualValue = value.asStrings()[currentRow];
						RNodeLog.logUserTrace(this, methodName, 7865, "Assigning R character value", iOwner.getName(), actualValue, column.getName(), columnElement, currentRow + 1, expectedRows, i + 1, iName);
						columnElement.setValue(actualValue);
					} else {
						columnElement.setValue(null);
					}
				} else {
					throw new RNodeException(this, methodName, 7849, "R column type is not supported (not logical/integer/double/character)", iName, iOwner.getName(), column.getName());
				}
				
			}
			
			// Move to the next row if there is one.
			if ((currentRow + 1) < expectedRows) {
				currentRow++;
			}
			
		}
		
	}
	
}
