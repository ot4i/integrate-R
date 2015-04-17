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

package com.ibm.broker.analytics.properties;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.widgets.Composite;

import com.ibm.etools.mft.flow.properties.ComplexPropertyEditor;

/**
 * @author zhongming
 *
 */
public class DataFrameTableEditor extends ComplexPropertyEditor {

	/**
	 *
	 */
	public DataFrameTableEditor() {
	}

	@Override
	public void createControls(Composite parent) {
		super.createControls(parent);

		// add Table Sorter
		tableViewer.setSorter(new ViewerSorter() {

			public String getDataFrameName(Object element) {
				if (!(element instanceof EObject)) {
					return "";
				}
				EObject e = (EObject) element;
				if (e.eClass().getEStructuralFeature("parameterDataFrame") == null) {
					return "";
				}
				Object o = e.eGet(e.eClass().getEStructuralFeature("parameterDataFrame"));
				if (o == null) {
					return "";
				}
				return o.toString();
			}

			public String getVariableName(Object element) {
				if (!(element instanceof EObject)) {
					return "";
				}
				EObject e = (EObject) element;
				if (e.eClass().getEStructuralFeature("parameterVariable") == null) {
					return "";
				}
				Object o = e.eGet(e.eClass().getEStructuralFeature("parameterVariable"));
				if (o == null) {
					return "";
				}
				return o.toString();
			}

			@Override
			public int category(Object element) {
				return getDataFrameName(element).hashCode();
			}

			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				String df1 = getDataFrameName(e1);
				String df2 = getDataFrameName(e2);
				if (df1.equals(df2)) {
					return getVariableName(e1).compareTo(getVariableName(e2));
				} else {
					return df1.compareTo(df2);
				}
			}
		});
	}
}
