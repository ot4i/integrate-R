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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import com.ibm.broker.plugin.MbBrokerException;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbInputTerminal;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbNamespaceBindings;
import com.ibm.broker.plugin.MbNode;
import com.ibm.broker.plugin.MbNodeInterface;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbTable;
import com.ibm.broker.plugin.MbXPath;
import com.ibm.broker.plugin.MbXPathVariables;

/**
 * An implementation of an IBM Integration Bus node for processing messages using the R statistical
 * programming language. The node establishes a connection to an Rserve server for processing scripts
 * written in R. Data can be passed to and from R by configuring parameters on the node.
 */
public class RNode extends MbNode implements MbNodeInterface {

	/**
	 * Get the node name used to create instances of this node.
	 * @return the node name
	 */
	public static String getNodeName() {
		return "ComIbmRNode";
	}
	
	/**
	 * The output terminal (out) that this node propagates messages to.
	 */
	private MbOutputTerminal iOutputTerminal;
	
	/**
	 * The connection factory used by this node to create connections to Rserve.
	 */
	private BasePooledObjectFactory<Connection> iConnectionFactory = null;
	
	/**
	 * The connection pool used by this node to manage connections to Rserve.
	 */
	private GenericObjectPool<Connection> iConnectionPool = null;
	
	/**
	 * The processed list of IN and INOUT variables used by this node to pass data to and from Rserve.
	 */
	private Map<String, RNodeVariable> iInVariables = new HashMap<>();
	
	/**
	 * The processed list of INOUT and OUT variables used by this node to pass data to and from Rserve.
	 */
	private Map<String, RNodeVariable> iOutVariables = new HashMap<>();
	
	/**
	 * The processed list of IN and INOUT data frames used by this node to pass data to and from Rserve.
	 */
	private Map<String, RNodeDataFrame> iInDataFrames = new HashMap<>();
	
	/**
	 * The processed list of INOUT and OUT data frames used by this node to pass data to and from Rserve.
	 */
	private Map<String, RNodeDataFrame> iOutDataFrames = new HashMap<>();
	
	/**
	 * The set of namespace bindings in use by the XPath expressions in the parameters used by this node.
	 */
	private MbNamespaceBindings iNamespaceBindings = null;
	
	/**
	 * The server configured on this node (in the format hostname[:port]).
	 */
	private String iServerProperty = "";
	
	/**
	 * The parsed server host name.
	 */
	private String iServerHostName = null;
	
	/**
	 * The parsed or defaulted server port.
	 */
	private int iServerPort = 6311;
	
	/**
	 * The path to the RData file that is loaded when this node establishes a connection to Rserve.
	 */
	private String iRDataFileProperty = "";
	
	/**
	 * An object containing the RData file contents that is loaded when this node establishes a connection to Rserve.
	 */
	private RNodeFile iRDataFile = null;
	
	/**
	 * The path to the script that is called when this node establishes a connection to Rserve.
	 */
	private String iConnectScriptProperty = "";
	
	/**
	 * An object containing the parsed script that is called when this node establishes a connection to Rserve.
	 */
	private RNodeScript iConnectScript = null;
	
	/**
	 * The path to the script that is called when this node processes a message.
	 */
	private String iEvaluateScriptProperty = "";

	/**
	 * An object containing the parsed script that is called when this node processes a message.
	 */
	private RNodeScript iEvaluateScript = null;
	
	/**
	 * The path to the script that is called before this node closes a connection to Rserve.
	 */
	private String iDisconnectScriptProperty = "";	
	
	/**
	 * An object containing the parsed script that is called before this node closes a connection to Rserve.
	 */
	private RNodeScript iDisconnectScript = null;
	
	/**
	 * The maximum number of established connections to Rserve that this node is allowed to make.
	 */
	private int iMaximumConnections = 10;
	
	/**
	 * The minimum number of established connections to Rserve that this node must keep available.
	 */
	private int iMinimumConnections = 1;
	
	/**
	 * The minimum period (in seconds) that an established connection to Rserve is allowed to remain idle before it is closed.
	 */
	private long iIdleConnectionTimeout = 10;
	
	/**
	 * Constructor for an RNode - creates the input and output terminals for this node.
	 * @throws MbException if the input and output terminals cannot be created.
	 */
	public RNode() throws MbException {
		
		// Create the input terminal.
		createInputTerminal("in");
		
		// Create the output and failure terminals.
		iOutputTerminal = createOutputTerminal("out");
		createOutputTerminal("failure");
		
		// Create the connection factory for this node.
		iConnectionFactory = new BasePooledObjectFactory<Connection>() {

			/**
			 * Called when the connection pool requests a new connection to Rserve.
			 * @return a new connection to Rserve.
			 */
			@Override
			public Connection create() throws RNodeException, MbException {
				final String methodName = "create";
				
				// Open a new connection to the specified Rserve server.
				RNodeLog.logUserTrace(this, methodName, 7836, "Connecting to Rserve server", getName(), iServerHostName, iServerPort);
				Connection connection = null;
				try {
					connection = new Connection(iServerHostName, iServerPort);
				} catch (RserveException e) {
					throw new RNodeException(this, methodName, 7839, "Failed to connect to Rserve server", getName(), iServerHostName, iServerPort, e.toString());
				}
				RNodeLog.logUserTrace(this, methodName, 7837, "Connected to Rserve server", getName(), iServerHostName, iServerPort);
				
				// If a RData file has been provided, load that now.
				if (iRDataFile != null) {
					try {
					
						// Check to see if the RData file has been updated.
						iRDataFile.update();
						
						// Assign the RData file contents to an R variable.
						String scriptVariable = ".iib_r_data_" + iRDataFile.getKey();
						connection.assign(scriptVariable, iRDataFile.getContent());
						
						// Load the RData file contents on the Rserve server.
						// We use try, as it also allows us to retrieve the error messages from the R runtime.
						REXP result = connection.parseAndEval("try(load(rawConnection(" + scriptVariable + ")),silent=TRUE)");
						if (result.inherits("try-error")) {
							throw new RNodeException(this, methodName, 7867, "R runtime failed to load file contents", getName(), iRDataFile.getFileName(), result.asString());
						}
					
					} catch (REngineException | REXPMismatchException e) {
						throw new RNodeException(this, methodName, 7867, "R runtime failed to load file contents", getName(), iRDataFile.getFileName(), e.getMessage());
					}
				}
				
				// If a connect script has been provided, run that before returning the connection to the pool for use.
				if (iConnectScript != null) {
					runScript(connection, iConnectScript);
				}
				return connection;
				
			}

			/**
			 * Called by the connection pool to wrap a new connection.
			 * @param the connection to wrap.
			 * @return the wrapped connection.
			 */
			@Override
			public PooledObject<Connection> wrap(Connection connection) {
				return new DefaultPooledObject<Connection>(connection);
			}
			
			/**
			 * Called by the connection pool to close an established connection.
			 * This occurs if the connection has remained idle for too long, or the node
			 * is being deleted.
			 * @param pooledConnection
			 */
			@Override
			public void destroyObject(PooledObject<Connection> pooledConnection) throws MbException {
				final String methodName = "destroyObject";
				
				// If a disconnect script has been provided, run that before closing the connection.
				Connection connection = pooledConnection.getObject();
				if (iDisconnectScript != null) {
					try {
						runScript(connection, iDisconnectScript);
					} catch (MbException e) {
						RNodeLog.logError(this, methodName, Long.valueOf(e.getMessageKey()), e.getTraceText(), e.getInserts());
					}
				}
				
				// Close the connection.
				connection.close();
				RNodeLog.logUserTrace(this, methodName, 7838, "Disconnected from Rserve server", getName(), iServerHostName, iServerPort);
				
			}
			
		};
		
	}
	
	/**
	 * Get the server configured on this node (in the format hostname[:port]).
	 * @return the server configured on this node.
	 */
	public String getServer() {
		return iServerProperty;
	}
	
	/**
	 * Set the server configured on this node (in the format hostname[:port]).
	 * If no port is provided, then the node defaults to port 6311.
	 * @param server the server to configure on this node (in the format hostname[:port])
	 */
	public void setServer(String server) {
		iServerProperty = server;
		int i = server.indexOf(":");
		if (i == -1) {
			iServerHostName = server;
			iServerPort = 6311;
		} else {
			String[] comps = server.split(":");
			iServerHostName = comps[0];
			iServerPort = Integer.parseInt(comps[1]);
		}
	}
	
	/**
	 * Get the path to the RData file that is loaded when this node establishes a connection to Rserve.
	 * @return the path to the script.
	 */
	public String getRDataFile() {
		return iRDataFileProperty;
	}
	
	/**
	 * Set the path to the RData file that is loaded when this node establishes a connection to Rserve.
	 * @param rdataFile the path to the RData file.
	 */
	public void setRDataFile(String rdataFile) {
		iRDataFileProperty = rdataFile;
	}
	
	/**
	 * Get the path to the script that is called when this node establishes a connection to Rserve.
	 * @return the path to the script.
	 */
	public String getConnectScript() {
		return iConnectScriptProperty;
	}
	
	/**
	 * Set the path to the script that is called when this node establishes a connection to Rserve.
	 * @param connectScript the path to the script.
	 */
	public void setConnectScript(String connectScript) {
		iConnectScriptProperty = connectScript;
	}
	
	/**
	 * Get the path to the script that is called when this node processes a message.
	 * @return the path to the script.
	 */
	public String getEvaluateScript() {
		return iEvaluateScriptProperty;
	}
	
	/**
	 * Set the path to the script that is called when this node processes a message.
	 * @param connectScript the path to the script.
	 */
	public void setEvaluateScript(String evaluateScript) {
		iEvaluateScriptProperty = evaluateScript;
	}
	
	/**
	 * Get the path to the script that is called before this node closes a connection to Rserve.
	 * @return the path to the script.
	 */
	public String getDisconnectScript() {
		return iDisconnectScriptProperty;
	}

	/**
	 * Set the path to the script that is called before this node closes a connection to Rserve.
	 * @param connectScript the path to the script.
	 */
	public void setDisconnectScript(String disconnectScript) {
		iDisconnectScriptProperty = disconnectScript;
	}
	
	/**
	 * Get the maximum number of established connections to Rserve that this node is allowed to make.
	 * @return the maximum number of established connections.
	 */
	public String getMaximumConnections() {
		return String.valueOf(iMaximumConnections);
	}
	
	/**
	 * Set the maximum number of established connections to Rserve that this node is allowed to make.
	 * @param maximumConnections the maximum number of established connections.
	 */
	public void setMaximumConnections(String maximumConnections) {
		iMaximumConnections = Integer.parseInt(maximumConnections);
	}
	
	/**
	 * Get the minimum number of established connections to Rserve that this node must keep available.
	 * @result the maximum number of established connections.
	 */
	public String getMinimumConnections() {
		return String.valueOf(iMinimumConnections);
	}
	
	/**
	 * Set the minimum number of established connections to Rserve that this node must keep available.
	 * @param minimumConnections the maximum number of established connections.
	 */
	public void setMinimumConnections(String minimumConnections) {
		iMinimumConnections = Integer.parseInt(minimumConnections);
	}

	/**
	 * Get the minimum period (in seconds) that an established connection to Rserve is allowed to remain idle before it is closed.
	 * @return the minimum period (in seconds).
	 */
	public String getIdleConnectionTimeout() {
		return String.valueOf(iIdleConnectionTimeout);
	}

	/**
	 * Set the minimum period (in seconds) that an established connection to Rserve is allowed to remain idle before it is closed.
	 * @param idleConnectionTimeout the minimum period (in seconds).
	 */
	public void setIdleConnectionTimeout(String idleConnectionTimeout) {
		iIdleConnectionTimeout = Integer.parseInt(idleConnectionTimeout);
	}
	
	/**
	 * Called by Integration Bus after an instance of this node has been created and configured, but before it processes any messages.
	 * @throws MbException if an exception occurs initializing this node.
	 */
	public void onInitialize() throws MbException {
		final String methodName = "onInitialize";
		
		// Load the RData file if one has been provided.
		iRDataFile = !iRDataFileProperty.isEmpty() ? new RNodeFile(this, iRDataFileProperty) : null;
		
		// Parse the connect script if one has been provided.
		iConnectScript = !iConnectScriptProperty.isEmpty() ? new RNodeScript(this, iConnectScriptProperty) : null;
		
		// Parse the evaluate script, which must always be provided.
		if (iEvaluateScriptProperty.isEmpty()) {
			throw new RNodeException(this, methodName, 7868, "Unrecognised parameter type", getName());
		}
		iEvaluateScript = new RNodeScript(this, iEvaluateScriptProperty);
		
		// Parse the disconnect script if one has been provided.
		iDisconnectScript = !iDisconnectScriptProperty.isEmpty() ? new RNodeScript(this, iDisconnectScriptProperty) : null;
		
		// Parse the namespace bindings table if one has been provided.
		iNamespaceBindings = new MbNamespaceBindings();
		MbTable nsMappingTable = (MbTable) getUserDefinedAttribute("nsMappingTable");
		if (nsMappingTable != null) {
			for (nsMappingTable.first(); nsMappingTable.lastMove(); nsMappingTable.next()) {
				String prefix = (String) nsMappingTable.getValue("nsPrefix");
				String uri = (String) nsMappingTable.getValue("namespace");
				iNamespaceBindings.addBinding(prefix, uri);
			}
		}
		
		// Mapping of variable and data frames to names.
		Map<String, RNodeVariable> variables = new HashMap<>();
		Map<String, RNodeDataFrame> dataFrames = new HashMap<>();
		
		// Parse the parameter table if one has been provided.
		MbTable parameterTable = (MbTable) getUserDefinedAttribute("parameterTable");
		if (parameterTable != null) {
			
			// Loop over all rows in the parameter table.
			for (parameterTable.first(); parameterTable.lastMove(); parameterTable.next()) {
				
				// Grab the parameter types from the row.
				String dataFrame = (String) parameterTable.getValue("parameterDataFrame");
				String name = (String) parameterTable.getValue("parameterName");
				String stringType = (String) parameterTable.getValue("parameterType");
				RNodeType type;
				try {
					type = RNodeType.fromString(stringType);
				} catch (IllegalArgumentException iae) {
					throw new RNodeException(this, methodName, 7812, "Unrecognised parameter type", getName(), name, stringType);
				}
				String stringDirection = (String) parameterTable.getValue("parameterDirection");
				RNodeDirection direction;
				try {
					direction = RNodeDirection.valueOf(stringDirection);
				} catch (IllegalArgumentException iae) {
					throw new RNodeException(this, methodName, 7813, "Unrecognised parameter direction", getName(), name, stringDirection);
				}
				String xpathExpression = (String) parameterTable.getValue("xpathExpression");
				
				// Validate that it has a name and XPath expression.
				if (name == null || name.isEmpty()) {
					throw new RNodeException(this, methodName, 7814, "Parameter has an empty name", getName());
				} else if (xpathExpression == null || xpathExpression.isEmpty()) {
					throw new RNodeException(this, methodName, 7816, "Parameter has an empty XPath expression", getName());
				}
				
				// Parse the XPath expression.
				MbXPath xpath = new MbXPath(xpathExpression, iNamespaceBindings);
				
				// If this is a data frame, create it.
				if (type == RNodeType.DATA_FRAME) {
					if (!dataFrames.containsKey(name)) {
						dataFrames.put(name, new RNodeDataFrame(this, name, xpathExpression, xpath));
					} else {
						throw new RNodeException(this, methodName, 7869, "Two data frames with the same name", getName(), name);
					}
				
				// Else, if it's a column in a data frame column add it.
				} else if (dataFrame != null && !dataFrame.isEmpty()) {
					RNodeDataFrame actualDataFrame = dataFrames.get(dataFrame);
					if (actualDataFrame != null) {
						actualDataFrame.addColumn(name, type, direction, xpathExpression, xpath);
					} else {
						throw new RNodeException(this, methodName, 7870, "Data frame does not exist", getName(), actualDataFrame);
					}
					if (direction == RNodeDirection.IN || direction == RNodeDirection.INOUT) {
						iInDataFrames.put(dataFrame, actualDataFrame);
					}
					if (direction == RNodeDirection.OUT || direction == RNodeDirection.INOUT) {
						iOutDataFrames.put(dataFrame, actualDataFrame);
					}
				
				// Else, create a variable.
				} else {
					RNodeVariable variable = new RNodeVariable(this, name, type, direction, xpathExpression, xpath);
					if (!variables.containsKey(name)) {
						variables.put(name, variable);
					} else {
						throw new RNodeException(this, methodName, 7815, "Two variables with the same name", getName(), name);
					}
					if (direction == RNodeDirection.IN || direction == RNodeDirection.INOUT) {
						iInVariables.put(name, variable);
					}
					if (direction == RNodeDirection.OUT || direction == RNodeDirection.INOUT) {
						iOutVariables.put(name, variable);
					}
				}
				
			}
			
		}
		
		// Catch any exceptions - we must shutdown the pool if we fail to open a connecton.
		try {

			// Create a new connection pool for this node.
			iConnectionPool = new GenericObjectPool<Connection>(iConnectionFactory);
			
			// Configure the connection pool.
			iConnectionPool.setMaxTotal(iMaximumConnections);
			iConnectionPool.setMaxIdle(Integer.MAX_VALUE);
			iConnectionPool.setMinIdle(iMinimumConnections);
			iConnectionPool.setMinEvictableIdleTimeMillis(Long.MAX_VALUE);
			iConnectionPool.setSoftMinEvictableIdleTimeMillis(iIdleConnectionTimeout * 1000);
			iConnectionPool.setNumTestsPerEvictionRun(iMaximumConnections);
			iConnectionPool.setTimeBetweenEvictionRunsMillis(100);
			
		} catch (Exception e) {
			
			// Shutdown the pool and rethrow the exception.
			if (iConnectionPool != null) {
				iConnectionPool.close();
				iConnectionPool = null;
			}
			throw e;
			
		}
		
	}
	
	/**
	 * Called by Integration Bus to process a message.
	 * @param assembly the input message.
	 * @param inputTerminal the input terminal the input message arrived on.
	 * @throws RNodeException if a problem occurs processing the input message.
	 * @throws MbException if a problem occurs during user trace processing.
	 */
	@Override
	public void evaluate(MbMessageAssembly assembly, MbInputTerminal inputTerminal) throws RNodeException, MbException {
		final String methodName = "evaluate";
		
		// Create a copy of the input message to propagate to the output terminal.
		MbMessage inMessage = assembly.getMessage();
		MbMessage outMessage = new MbMessage(inMessage);
		MbMessageAssembly outAssembly = new MbMessageAssembly(assembly, outMessage);
		
		// Set up the XPath variable bindings with the paths to parts of the input message.
		MbXPathVariables xpathVariables = new MbXPathVariables();
		xpathVariables.assign("InputRoot", outMessage.getRootElement());
		xpathVariables.assign("Root", outMessage.getRootElement());
		xpathVariables.assign("OutputRoot", outMessage.getRootElement());
		xpathVariables.assign("InputLocalEnvironment", outAssembly.getLocalEnvironment().getRootElement());
		xpathVariables.assign("LocalEnvironment", outAssembly.getLocalEnvironment().getRootElement());
		xpathVariables.assign("OutputLocalEnvironment", outAssembly.getLocalEnvironment().getRootElement());
		xpathVariables.assign("InputDestinationList", outAssembly.getLocalEnvironment().getRootElement());
		xpathVariables.assign("DestinationList", outAssembly.getLocalEnvironment().getRootElement());
		xpathVariables.assign("OutputDestinationList", outAssembly.getLocalEnvironment().getRootElement());
		xpathVariables.assign("InputExceptionList", outAssembly.getExceptionList().getRootElement());
		xpathVariables.assign("ExceptionList", outAssembly.getExceptionList().getRootElement());
		xpathVariables.assign("OutputExceptionList", outAssembly.getExceptionList().getRootElement());
		xpathVariables.assign("Environment", outAssembly.getGlobalEnvironment().getRootElement());
		
		Connection connection = null;
		try {
			
			// Request a connection from the connection pool.
			// This will block until a connection becomes available.
			connection = iConnectionPool.borrowObject();
			
			// Process any IN and INOUT variables.
			for (RNodeVariable variable : iInVariables.values()) {
				variable.toR(connection, inMessage, xpathVariables);
			}
			
			// Process any IN and INOUT data frames.
			for (RNodeDataFrame dataFrame : iInDataFrames.values()) {
				dataFrame.toR(connection, inMessage, xpathVariables);
			}
			
			// Run the evaluate script.
			runScript(connection, iEvaluateScript);
			
			// Process any INOUT and OUT data frames.
			for (RNodeDataFrame dataFrame : iOutDataFrames.values()) {
				dataFrame.fromR(connection, outMessage, xpathVariables);
			}
			
			// Process any INOUT and OUT variables.
			for (RNodeVariable variable : iOutVariables.values()) {
				variable.fromR(connection, outMessage, xpathVariables);
			}
						
			// Propagate the output message to the output terminal.
			iOutputTerminal.propagate(outAssembly);
			
		} catch (RNodeException e) {
			throw e;
		} catch (MbException | MbBrokerException mbe) {
			throw mbe;
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			throw new RNodeException(this, methodName, 7866, "Unknown exception caught", e.toString(), sw.toString());
		} finally {
			
			// Ensure that the connection is returned to the connection pool.
			if (connection != null) {
				iConnectionPool.returnObject(connection);
			}
			
		}
	}
	
	/**
	 * Called by Integration Bus just before this instance of the node is deleted.
	 */
	public void onDelete() {
		
		// Close all active connections to Rserve.
		if (iConnectionPool != null) {
			iConnectionPool.close();
			iConnectionPool = null;
		}
		
	}
	
	/**
	 * Parse and evaluate the specified script on the provided Rserve connection.
	 * The script will be checked to see if has been updated before it is parsed and evaluated.
	 * @param connection the established Rserve connection to use.
	 * @param script the script to parse and evaluate.
	 * @throws RNodeException if a problem occurs parsing or evaluating the specified script.
	 * @throws MbException if a problem occurs during user trace processing.
	 */
	private void runScript(Connection connection, RNodeScript script) throws RNodeException, MbException {
		final String methodName = "runScript";
		RNodeLog.logUserTrace(this, methodName, 7826, "About to evaluate file using R runtime", getName(), script.getFileName());
		try {
			
			// Check to see if the script has been updated.
			script.update();
			
			// Check to see if we need to update the parsed script.
			// The file may have been updated since we last parsed it.
			String parsedScriptVariable = ".iib_r_parsed_script_" + script.getKey();
			Lock readLock = script.getReadLock();
			readLock.lock();
			try {
				if (connection.checkScriptVersion(script.getKey(), script.getVersion())) {
					
					// Assign the script contents to an R variable.
					String scriptVariable = ".iib_r_script_" + script.getKey();
					connection.assign(scriptVariable, script.getContent());
					
					// Parse the script contents on the Rserve server into an R language object.
					// We use try, as it also allows us to retrieve the error messages from the R runtime.
					REXP result = connection.parseAndEval("try(" + parsedScriptVariable + " <- parse(text=" + scriptVariable + "),silent=TRUE)");
					if (result.inherits("try-error")) {
						throw new RNodeException(this, methodName, 7810, "R runtime failed to parse file contents", getName(), script.getFileName(), result.asString());
					}
					
				}
			} finally {
				readLock.unlock();
			}
			
			// Evaluate the R script on the Rserve server.
			// We use try, as it also allows us to retrieve the error messages from the R runtime.
			REXP result = connection.parseAndEval("try(eval(" + parsedScriptVariable + "),silent=TRUE)");
			
			// Check to see if an error occurred - if so, throw an exception containing the error messages.
			if (result.inherits("try-error")) {
				throw new RNodeException(this, methodName, 7811, "R runtime failed to evaluate file contents", getName(), script.getFileName(), result.asString());
			}
			RNodeLog.logUserTrace(this, methodName, 7827, "R runtime successfully evaluated file", getName(), script.getFileName());
			
		} catch (REngineException | REXPMismatchException e) {
			throw new RNodeException(this, methodName, 7811, "R runtime failed to evaluate file contents", getName(), script.getFileName(), e.getMessage());
		}
	}
	
	/**
	 * A small class built on top of an Rserve connection that maintains a per-connection
	 * mapping of parsed scripts and the versions of those parsed scripts.
	 */
	private class Connection extends RConnection {
		
		/**
		 * The mapping of parsed scripts and their current versions.
		 */
		private Map<String, Long> iScriptVersions;
		
		/**
		 * Constructor for a connection - connect to an Rserve server.
		 * @param hostname the Rserve server hostname.
		 * @param port the Rserve server port.
		 * @throws RserveException if an exception occurs connecting to the Rserve server.
		 */
		public Connection(String hostname, int port) throws RserveException {
			super(hostname, port);
			iScriptVersions = new HashMap<>();
		}
		
		/**
		 * Check to see if the specified script has been parsed by this connection, and
		 * if it has whether the parsed version is the current version.
		 * @param key the key used to identify this script.
		 * @param currentVersion the current version of this script.
		 * @return true if the specified script has not been parsed or is out of date.
		 */
		public boolean checkScriptVersion(String key, long currentVersion) {
			Long parsedVersion = iScriptVersions.get(key);
			boolean result = parsedVersion == null || !parsedVersion.equals(currentVersion);
			if (result)
				iScriptVersions.put(key, currentVersion);
			return result;
		}
		
	}
	
}
