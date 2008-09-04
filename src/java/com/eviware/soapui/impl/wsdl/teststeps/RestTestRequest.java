/*
 *  soapUI, copyright (C) 2004-2008 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl.teststeps;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.AttachmentConfig;
import com.eviware.soapui.config.RestMethodConfig;
import com.eviware.soapui.config.TestAssertionConfig;
import com.eviware.soapui.impl.rest.RestRequest;
import com.eviware.soapui.impl.rest.RestResource;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.settings.XmlBeansSettingsImpl;
import com.eviware.soapui.impl.wsdl.submit.transports.http.HttpResponse;
import com.eviware.soapui.impl.wsdl.support.assertions.AssertableConfig;
import com.eviware.soapui.impl.wsdl.support.assertions.AssertionsSupport;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestRunContext;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.TestAssertionRegistry.AssertableType;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.Submit;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.testsuite.Assertable;
import com.eviware.soapui.model.testsuite.AssertionsListener;
import com.eviware.soapui.model.testsuite.TestAssertion;
import com.eviware.soapui.monitor.TestMonitor;
import com.eviware.soapui.support.Tools;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.resolver.ResolveContext;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RestTestRequest extends RestRequest implements Assertable, TestRequest
{
   public static final String RESPONSE_PROPERTY = RestTestRequest.class.getName() + "@response";
   public static final String STATUS_PROPERTY = RestTestRequest.class.getName() + "@status";

   private static ImageIcon validRequestIcon;
   private static ImageIcon failedRequestIcon;
   private static ImageIcon disabledRequestIcon;
   private static ImageIcon unknownRequestIcon;

   private AssertionStatus currentStatus;
   private HttpTestRequestStep testStep;

   private AssertionsSupport assertionsSupport;
   private RestResponseMessageExchange messageExchange;
   private final boolean forLoadTest;
   private PropertyChangeNotifier notifier;

   public RestTestRequest( RestResource resource, RestMethodConfig callConfig, HttpTestRequestStep testStep,
                           boolean forLoadTest )
   {
      super( resource, callConfig, forLoadTest );
      this.forLoadTest = forLoadTest;

      setSettings( new XmlBeansSettingsImpl( this, testStep.getSettings(), callConfig.getSettings() ) );

      this.testStep = testStep;

      initAssertions();
      initIcons();
   }

   public ModelItem getParent()
   {
      return getTestStep();
   }

   public WsdlTestCase getTestCase()
   {
      return testStep.getTestCase();
   }

   protected void initIcons()
   {
      if( validRequestIcon == null )
         validRequestIcon = UISupport.createImageIcon( "/valid_request.gif" );

      if( failedRequestIcon == null )
         failedRequestIcon = UISupport.createImageIcon( "/invalid_request.gif" );

      if( unknownRequestIcon == null )
         unknownRequestIcon = UISupport.createImageIcon( "/unknown_request.gif" );

      if( disabledRequestIcon == null )
         disabledRequestIcon = UISupport.createImageIcon( "/disabled_request.gif" );
   }

   @Override
   protected RequestIconAnimator<?> initIconAnimator()
   {
      return new TestRequestIconAnimator( this );
   }

   private void initAssertions()
   {
      assertionsSupport = new AssertionsSupport( testStep, new AssertableConfig()
      {

         public TestAssertionConfig addNewAssertion()
         {
            return getConfig().addNewAssertion();
         }

         public List<TestAssertionConfig> getAssertionList()
         {
            return getConfig().getAssertionList();
         }

         public void removeAssertion( int ix )
         {
            getConfig().removeAssertion( ix );
         }
      } );
   }

   public int getAssertionCount()
   {
      return assertionsSupport.getAssertionCount();
   }

   public WsdlMessageAssertion getAssertionAt( int c )
   {
      return assertionsSupport.getAssertionAt( c );
   }

   public void setResponse( HttpResponse response, SubmitContext context )
   {
      HttpResponse oldResponse = getResponse();
      super.setResponse( response, context );

      if( response != oldResponse )
         assertResponse( context );
   }

   public void assertResponse( SubmitContext context )
   {
      if( notifier == null )
         notifier = new PropertyChangeNotifier();

      messageExchange = new RestResponseMessageExchange( this );

      // assert!
      for( WsdlMessageAssertion assertion : assertionsSupport.getAssertionList() )
      {
         assertion.assertResponse( messageExchange, context );
      }

      notifier.notifyChange();
   }

   private class PropertyChangeNotifier
   {
      private AssertionStatus oldStatus;
      private ImageIcon oldIcon;

      public PropertyChangeNotifier()
      {
         oldStatus = getAssertionStatus();
         oldIcon = getIcon();
      }

      public void notifyChange()
      {
         AssertionStatus newStatus = getAssertionStatus();
         ImageIcon newIcon = getIcon();

         if( oldStatus != newStatus )
            notifyPropertyChanged( STATUS_PROPERTY, oldStatus, newStatus );

         if( oldIcon != newIcon )
            notifyPropertyChanged( ICON_PROPERTY, oldIcon, getIcon() );

         oldStatus = newStatus;
         oldIcon = newIcon;
      }
   }

   public WsdlMessageAssertion addAssertion( String assertionLabel )
   {
      PropertyChangeNotifier notifier = new PropertyChangeNotifier();

      try
      {
         WsdlMessageAssertion assertion = assertionsSupport.addWsdlAssertion( assertionLabel );
         if( assertion == null )
            return null;

         if( getResponse() != null )
         {
            assertion.assertResponse( new RestResponseMessageExchange( this ), new WsdlTestRunContext( testStep ) );
            notifier.notifyChange();
         }

         return assertion;
      }
      catch( Exception e )
      {
         SoapUI.logError( e );
         return null;
      }
   }

   public void removeAssertion( TestAssertion assertion )
   {
      PropertyChangeNotifier notifier = new PropertyChangeNotifier();

      try
      {
         assertionsSupport.removeAssertion( (WsdlMessageAssertion) assertion );

      }
      finally
      {
         ((WsdlMessageAssertion) assertion).release();
         notifier.notifyChange();
      }
   }

   public AssertionStatus getAssertionStatus()
   {
      currentStatus = AssertionStatus.UNKNOWN;

      if( messageExchange != null )
      {
         if( !messageExchange.hasResponse() &&
                 getOperation().isBidirectional() )
         {
            currentStatus = AssertionStatus.FAILED;
         }
      }
      else
         return currentStatus;

      int cnt = getAssertionCount();
      if( cnt == 0 )
         return currentStatus;

      for( int c = 0; c < cnt; c++ )
      {
         if( getAssertionAt( c ).getStatus() == AssertionStatus.FAILED )
         {
            currentStatus = AssertionStatus.FAILED;
            break;
         }
      }

      if( currentStatus == AssertionStatus.UNKNOWN )
         currentStatus = AssertionStatus.VALID;

      return currentStatus;
   }

   @Override
   public ImageIcon getIcon()
   {
      if( forLoadTest )
         return null;

      TestMonitor testMonitor = SoapUI.getTestMonitor();
      if( testMonitor != null && testMonitor.hasRunningLoadTest( testStep.getTestCase() ) )
         return disabledRequestIcon;

      ImageIcon icon = getIconAnimator().getIcon();
      if( icon == getIconAnimator().getBaseIcon() )
      {
         AssertionStatus status = getAssertionStatus();
         if( status == AssertionStatus.VALID )
            return validRequestIcon;
         else if( status == AssertionStatus.FAILED )
            return failedRequestIcon;
         else if( status == AssertionStatus.UNKNOWN )
            return unknownRequestIcon;
      }

      return icon;
   }

   public void addAssertionsListener( AssertionsListener listener )
   {
      assertionsSupport.addAssertionsListener( listener );
   }

   public void removeAssertionsListener( AssertionsListener listener )
   {
      assertionsSupport.removeAssertionsListener( listener );
   }

   /**
    * Called when a testrequest is moved in a testcase
    */

   public void updateConfig( RestMethodConfig request )
   {
      super.updateConfig( request );

      assertionsSupport.refresh();

      List<AttachmentConfig> attachmentConfigs = getConfig().getAttachmentList();
      for( int i = 0; i < attachmentConfigs.size(); i++ )
      {
         AttachmentConfig config = attachmentConfigs.get( i );
         getAttachmentsList().get( i ).updateConfig( config );
      }
   }

   @Override
   public void release()
   {
      super.release();
      assertionsSupport.release();
   }

   public String getAssertableContent()
   {
      return getResponseContentAsXml();
   }

   public HttpTestRequestStep getTestStep()
   {
      return testStep;
   }

   public RestService getInterface()
   {
      return getOperation() == null ? null : getOperation().getInterface();
   }

   @Override
   public RestResource getOperation()
   {
      return testStep instanceof RestTestRequestStep ? ((RestTestRequestStep)testStep).getResource() : null;
   }

   protected static class TestRequestIconAnimator extends RequestIconAnimator<RestTestRequest>
   {
      public TestRequestIconAnimator( RestTestRequest modelItem )
      {
         super( modelItem, "/request.gif", "/exec_request", 4, "gif" );
      }

      @Override
      public boolean beforeSubmit( Submit submit, SubmitContext context )
      {
         if( SoapUI.getTestMonitor() != null && SoapUI.getTestMonitor().hasRunningLoadTest( getTarget().getTestCase() ) )
            return true;

         return super.beforeSubmit( submit, context );
      }

      @Override
      public void afterSubmit( Submit submit, SubmitContext context )
      {
         if( submit.getRequest() == getTarget() )
            stop();
      }
   }

   public AssertableType getAssertableType()
   {
      return AssertableType.RESPONSE;
   }

   public TestAssertion cloneAssertion( TestAssertion source, String name )
   {
      return assertionsSupport.cloneAssertion( source, name );
   }

   public WsdlMessageAssertion importAssertion( WsdlMessageAssertion source, boolean overwrite, boolean createCopy )
   {
      return assertionsSupport.importAssertion( source, overwrite, createCopy );
   }

   public List<TestAssertion> getAssertionList()
   {
      return new ArrayList<TestAssertion>( assertionsSupport.getAssertionList() );
   }

   public WsdlMessageAssertion getAssertionByName( String name )
   {
      return assertionsSupport.getAssertionByName( name );
   }

   public ModelItem getModelItem()
   {
      return testStep;
   }

   public Map<String, TestAssertion> getAssertions()
   {
      return assertionsSupport.getAssertions();
   }

   public String getDefaultAssertableContent()
   {
      return "";
   }

   public String getResponseContentAsString()
   {
      return getResponse() == null ? null : getResponse().getContentAsString();
   }

   public void setPath( String fullPath )
   {
      super.setPath( fullPath );

      if( getOperation() == null )
      {
         try
         {
            setEndpoint( Tools.getEndpointFromUrl( new URL( fullPath ) ) );
         }
         catch( MalformedURLException e )
         {

         }
      }
   }

   public void setResource( RestResource wsdlOperation )
   {

   }



   public void resolve( ResolveContext context )
   {
      super.resolve( context );

      assertionsSupport.resolve( context );
      
   }
}
