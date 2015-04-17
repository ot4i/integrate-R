integrate-R
===========

The R node included in this repository enables you to extend the capability of IBM Integration Bus to connect to an RServe server and execute scripts written in the R statistical programming language as part of a message flow. Data can be extracted from the inbound message and passed into those scripts as inputs (R variables) for use in calculations. Once the script has finished executing, then the value of any outputs (R variables) can be extracted and inserted into the outbound message.

##Dependencies
Install and configure [IBM Integration Bus](http://www.ibm.com/software/products/us/en/integration-bus/). V10.0 (or later) is required in order to use the R node.

Install the [R](http://www.r-project.org/) runtime.

Install and configure an [Rserve](https://rforge.net/Rserve/) server. The system on which you install IBM Integration Bus and deploy R nodes to must be able to communicate with the Rserve server over TCP/IP.

Note that the R runtime and Rserve server may not be available for the system on which you install IBM Integration Bus on. For example, no R runtime is available for z/OS. However, you can deploy the R node to any system that supports IBM Integration Bus, and the R node can communicate with an Rserve server running on another system that supports the R runtime and Rserve server over TCP/IP.

##Setup
1. Install the R node runtime component into IBM Integration Bus. 
  * You can download a prebuilt binary from the [RNode](RNode) project. The file is named *RNodeRuntime-<version>.par*.
  * Alternatively, you can download and build the source in the [RNodeRuntime](RNodeRuntime) project.
  * Copy the *RNodeRuntime-<version>.par* file into the IBM Integration Bus installation. The file should be copied into *<install root>\server\lil*.
  * Once the file has been copied, then you must restart all integration servers that you will be deploying the R node to.

2. Install the R node toolkit component into IBM Integration Bus.
  * You can download a prebuilt archive from the [RNode](RNode) project. The file is named *RNodeToolkit-<version>.zip*.
  * Alternatively, you can download and build the source in the [RNodeToolkit](RNodeToolkit) project.
  * Extract the *RNodeToolkit-<version>.zip* file into the IBM Integration Bus installation. The file should be extracted into *<install root>\tools*, and the extract process should create a file named *<install root>\tools\plugins\RNodeToolkit-<version>.jar*.
  * Once the file has been extracted, then you must restart the Integration Toolkit.

3. Verify the installation. Once the Integration Toolkit has been restarted, the R node should be available from the palette in the message flow editor. The R node is displayed under the Analytics category.

##Copyright and license
Copyright 2014 IBM Corp. under the [Eclipse Public license](http://www.eclipse.org/legal/epl-v10.html).
