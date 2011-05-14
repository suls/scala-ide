/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse.codeanalysis

import scala.tools.eclipse.SettingConverterUtil
import org.eclipse.core.runtime.{CoreException, Platform}
import scala.tools.eclipse.ScalaPlugin
import scala.tools.nsc.Global
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IMarker
import scala.util.control.Exception._
import scala.tools.eclipse.properties.ScalaPluginSettings

object CodeAnalysisExtensionPoint {
  
  val PARTICIPANTS_ID = "org.scala-ide.sdt.core.scalaCodeAnalysis"
  
  val MARKER_TYPE = "org.scala-ide.sdt.core.scalaCodeAnalysisProblem"
  
  trait CompilationUnit {
    val global: Global
    val unit: global.CompilationUnit
  }
  
  case class ExtensionPointDescription(id: String, name: String, markerId: String, severity: Int)
    
  def apply(file: IFile, cu: CompilationUnit) = {
        
    deleteMarkers(file)

    extensions map {
      case (ExtensionPointDescription(analyzerId, _, markerType, _), extension) =>
        
        if(CodeAnalysisPreferences.isEnabledForProject(file.getProject, analyzerId)) {
                  
          lazy val severity = CodeAnalysisPreferences.getSeverityForProject(file.getProject, analyzerId)
          
          extension.analyze(cu) foreach {
            case extension.Marker(msg, line) => 
              addMarker(file, markerType, msg, line, severity)
          }
        }
    }
  }
    
  lazy val extensions: List[(ExtensionPointDescription, CodeAnalysisExtension)] = {
    
    val configs = Platform.getExtensionRegistry.getConfigurationElementsFor(PARTICIPANTS_ID).toList

    configs flatMap { e =>
      
      val (markerType, severity) = e.getChildren.toList match {
        
        case child :: Nil =>
          
          val markerId = Option(child.getAttribute("id")) getOrElse MARKER_TYPE          
          val severity = Option(child.getAttribute("severity")) flatMap {
              catching(classOf[NumberFormatException]) opt _.toInt
            } getOrElse IMarker.SEVERITY_WARNING
          
          (markerId, severity)
          
        case _ => 
          (MARKER_TYPE, IMarker.SEVERITY_WARNING)
      }
      
      val analyzerName = e.getAttribute("name")
      val analyzerId   = e.getAttribute("id")
      
      catching(classOf[CoreException]) opt e.createExecutableExtension("class") collect {
        case instance: CodeAnalysisExtension => 
          (ExtensionPointDescription(analyzerId, analyzerName, markerType, severity), instance)
      }
    }
  }
  
  def isEnabled = {
    ScalaPlugin.plugin.getPreferenceStore.getBoolean(SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.codeAnalysis.name))
  }
    
  private def deleteMarkers(file: IFile) {
    file.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO)
  }
  
  private def addMarker(file: IFile, markerType: String, message: String, lineNumber: Int, severity: Int) {
    val marker = file.createMarker(markerType)
    marker.setAttribute(IMarker.MESSAGE, message)
    marker.setAttribute(IMarker.SEVERITY, severity)
    marker.setAttribute(IMarker.LINE_NUMBER, if (lineNumber == -1) 1 else lineNumber)
  }
}
