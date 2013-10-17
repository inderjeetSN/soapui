/*
 *  SoapUI, copyright (C) 2004-2013 smartbear.com
 *
 *  SoapUI is free software; you can redistribute it and/or modify it under the
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  SoapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.rest.panels.resource;

import com.eviware.soapui.impl.rest.support.RestParamProperty;
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder;
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder.ParameterStyle;
import com.eviware.soapui.impl.rest.support.RestUtils;
import com.eviware.soapui.impl.support.actions.ShowOnlineHelpAction;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.AddParamAction;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.MovePropertyDownAction;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.MovePropertyUpAction;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.RemovePropertyAction;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.JXToolBar;
import com.eviware.soapui.support.components.SimpleBindingForm;
import com.eviware.soapui.support.components.StringListFormComponent;
import com.jgoodies.binding.PresentationModel;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlBeans;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.namespace.QName;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import static com.eviware.soapui.impl.rest.actions.support.NewRestResourceActionBase.ParamLocation;


public class RestParamsTable extends JPanel
{
	protected RestParamsPropertyHolder params;
	protected RestParamsTableModel paramsTableModel;
	protected JTable paramsTable;
	protected AddParamAction addParamAction = null;
	protected RemovePropertyAction removeParamAction = null;
	protected UseDefaultParamsAction defaultParamsAction = null;
	protected MovePropertyDownAction movePropertyDownAction = null;
	protected MovePropertyUpAction movePropertyUpAction = null;
	protected UpdateParamsAction updateParamsAction = null;
	private PresentationModel<RestParamProperty> paramDetailsModel;
	private SimpleBindingForm detailsForm;
	private boolean showEditableButtons;
	private boolean showDefaultParamsButton;

	public RestParamsTable( RestParamsPropertyHolder params, boolean showInspector, ParamLocation defaultParamLocation,
									boolean showEditableButtons, boolean showDefaultParamsButton )
	{
		this( params, showInspector, new RestParamsTableModel( params ), defaultParamLocation, showEditableButtons,
				showDefaultParamsButton );
	}

	public RestParamsTable( RestParamsPropertyHolder params, boolean showInspector, RestParamsTableModel model,
									ParamLocation defaultParamLocation, boolean showEditableButtons, boolean showDefaultParamsButton )
	{
		super( new BorderLayout() );
		this.showEditableButtons = showEditableButtons;
		this.showDefaultParamsButton = showDefaultParamsButton;
		this.params = params;
		this.paramsTableModel = model;
		init( showInspector );
	}

	protected void init( boolean showInspector )
	{
		paramsTable = new JTable( paramsTableModel );

		if( showDefaultParamsButton )
		{
			defaultParamsAction = new UseDefaultParamsAction();
		}

		movePropertyDownAction = new MovePropertyDownAction( paramsTable, params, "Moves selected parameter down one row" );
		movePropertyUpAction = new MovePropertyUpAction( paramsTable, params, "Moves selected parameter up one row" );


		if( showEditableButtons )
		{
			initEditableButtons();
		}


		paramsTable.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		paramsTable.setDefaultEditor( ParameterStyle.class, new DefaultCellEditor(
				new JComboBox<ParameterStyle>( paramsTableModel.getParameterStylesForEdit() ) ) );
		paramsTable.setDefaultEditor( ParamLocation.class, new DefaultCellEditor(
				new JComboBox<ParamLocation>( paramsTableModel.getParameterLevels() ) ) );
		// Workaround: for some reason the lower part of text gets clipped on some platforms
		paramsTable.setRowHeight( 25 );
		paramsTable.getSelectionModel().addListSelectionListener( new ListSelectionListener()
		{

			public void valueChanged( ListSelectionEvent e )
			{
				int selectedRow = paramsTable.getSelectedRow();
				if( showEditableButtons )
				{
					removeParamAction.setEnabled( selectedRow != -1 );
				}
				if( showDefaultParamsButton )
				{
					defaultParamsAction.setEnabled( paramsTable.getRowCount() > 0 );
				}
				movePropertyDownAction.setEnabled( selectedRow < paramsTable.getRowCount() - 1 );
				movePropertyUpAction.setEnabled( selectedRow > 0 );

				if( selectedRow != -1 )
				{
					RestParamProperty selectedParameter = getSelectedParameter();
					if( paramDetailsModel != null )
					{
						paramDetailsModel.setBean( selectedParameter );
						detailsForm.setEnabled( true );
					}
				}
				else
				{
					if( paramDetailsModel != null )
					{
						detailsForm.setEnabled( false );
						paramDetailsModel.setBean( null );
					}
				}
			}
		} );

		add( buildToolbar(), BorderLayout.NORTH );

		if( showInspector )
		{
			final JSplitPane splitPane = UISupport.createVerticalSplit( new JScrollPane( paramsTable ), buildDetails() );
			add( splitPane, BorderLayout.CENTER );

			splitPane.setResizeWeight( 0.7 );
		}
		else
		{
			add( new JScrollPane( paramsTable ), BorderLayout.CENTER );
		}
	}

	@Override
	public synchronized void addKeyListener( KeyListener l )
	{
		super.addKeyListener( l );
		paramsTable.addKeyListener( l );
	}

	private void initEditableButtons()
	{
		addParamAction = new AddParamAction( paramsTable, params, "Adds a parameter to the parameter table" );
		removeParamAction = new RemovePropertyAction( paramsTable, params, "Removes the selected parameter" );
		updateParamsAction = new UpdateParamsAction();

	}

	private JComponent buildDetails()
	{
		paramDetailsModel = new PresentationModel<RestParamProperty>( null );
		detailsForm = new SimpleBindingForm( paramDetailsModel );

		detailsForm.addSpace( 5 );
		detailsForm.appendCheckBox( "required", "Required", "Sets if parameter is required" );
		// form.appendTextField( "defaultValue", "Default",
		// "The default value for this parameter" );

		List<QName> types = new ArrayList<QName>();
		for( SchemaType type : XmlBeans.getBuiltinTypeSystem().globalTypes() )
		{
			types.add( type.getName() );
		}

		detailsForm.appendComboBox( "type", "Type", types.toArray(), "The type of the parameter" );
		StringListFormComponent optionsFormComponent = new StringListFormComponent( "Available values for this Parameter" );
		//TODO: Consider removing hardcoded size
		optionsFormComponent.setPreferredSize( new Dimension( 350, 100 ) );
		detailsForm.appendComponent( "options", "Options", optionsFormComponent );
		detailsForm.appendTextField( "description", "Description", "A short description of the parameter" );
		detailsForm.appendCheckBox( "disableUrlEncoding", "Disable Encoding",
				"Disables URL-Encoding of the parameter value" );

		detailsForm.addSpace( 5 );

		detailsForm.setEnabled( false );

		return new JScrollPane( detailsForm.getPanel() );
	}

	protected RestParamProperty getSelectedParameter()
	{
		return paramsTable.getSelectedRow() == -1 ? null : paramsTableModel.getParameterAt( paramsTable.getSelectedRow() );
	}

	public JTable getParamsTable()
	{
		return paramsTable;
	}

	public void release()
	{
		paramsTableModel.release();
		if( paramDetailsModel != null )
			paramDetailsModel.setBean( null );
	}

	protected Component buildToolbar()
	{
		JXToolBar toolbar = UISupport.createToolbar();

		//TODO: the action should be disabled by default if the corresponding component (button)
		// is supposed to be disabled by default.
		if( showEditableButtons )
		{
			toolbar.add( UISupport.createToolbarButton( addParamAction ) );
			toolbar.add( UISupport.createToolbarButton( removeParamAction, false ) );
			toolbar.addSeparator();
			toolbar.add( UISupport.createToolbarButton( updateParamsAction ) );
		}

		if( showDefaultParamsButton )
		{
			toolbar.add( UISupport.createToolbarButton( defaultParamsAction, paramsTable.getRowCount() > 0 ) );
		}

		toolbar.addSeparator();
		if( inFullMode() )
		{
			toolbar.add( UISupport.createToolbarButton( movePropertyDownAction, false ) );
			toolbar.add( UISupport.createToolbarButton( movePropertyUpAction, false ) );
		}
		toolbar.addSeparator();

		toolbar.addGlue();

		toolbar.add( UISupport.createToolbarButton( new ShowOnlineHelpAction( HelpUrls.WADL_PARAMS_HELP_URL ) ) );

		return toolbar;
	}

	private boolean inFullMode()
	{
		RestParamsTableModel tableModel = ( RestParamsTableModel )getParamsTable().getModel();
		return tableModel.isInFullMode();
	}

	public void extractParams( RestParamsPropertyHolder params, ParamLocation location )
	{
		for( int i = 0; i < paramsTable.getRowCount(); i++ )
		{
			RestParamProperty prop = paramsTableModel.getParameterAt( i );
			if( paramsTableModel.getParamLocationAt( i ) == location )
			{
				params.addParameter( prop );
			}
		}
	}

	public void focusParameter( String parameterName )
	{
		paramsTable.grabFocus();
		for( int i = 0; i < paramsTable.getRowCount(); i++ )
		{
			if( paramsTable.getValueAt( i, 0 ).equals( parameterName ) )
			{
				paramsTable.setRowSelectionInterval( i, i );
				paramsTable.editCellAt( i, 1 );
				JTextField editorComponent = ( JTextField )paramsTable.getEditorComponent();
				editorComponent.grabFocus();
				editorComponent.selectAll();
				return;
			}
		}

	}

	private class UpdateParamsAction extends AbstractAction
	{
		private UpdateParamsAction()
		{
			putValue( Action.SMALL_ICON, UISupport.createImageIcon( "/update-request-parameters-from-url.png" ) );
			putValue( Action.SHORT_DESCRIPTION, "Updates params from a specified URL" );
		}

		public void actionPerformed( ActionEvent e )
		{
			String str = UISupport.prompt( "Enter new url below", "Extract Params", "" );
			if( str == null )
				return;

			try
			{
				params.resetValues();
				RestUtils.extractParams( str, params, false );
			}
			catch( Exception e1 )
			{
				UISupport.showErrorMessage( e1 );
			}
		}
	}

	private class UseDefaultParamsAction extends AbstractAction
	{
		public UseDefaultParamsAction()
		{
			putValue( Action.SMALL_ICON, UISupport.createImageIcon( "/default_properties.gif" ) );
			putValue( Action.SHORT_DESCRIPTION, "Reverts all current parameters to default values" );
			setEnabled( false );
		}

		public void actionPerformed( ActionEvent e )
		{
			if( UISupport.confirm( "Revert all parameters to default values?", "Use Defaults" ) )
			{
				for( TestProperty property : params.getProperties().values() )
				{
					property.setValue( null );
				}
			}
		}
	}

	public void setParams( RestParamsPropertyHolder params )
	{
		this.params = params;
		paramsTableModel.setParams( params );
	}

	public void refresh()
	{
		paramsTableModel.fireTableDataChanged();
	}
}
