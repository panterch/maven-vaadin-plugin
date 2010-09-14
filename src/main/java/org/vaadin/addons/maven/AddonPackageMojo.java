package org.vaadin.addons.maven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.Manifest;

import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.util.FileUtils;

/**
 * Creates a vaadin addon zip package from the project
 * 
 * @goal addon
 * @requiresDependencyResolution test
 */
public class AddonPackageMojo extends AbstractMojo {
  /**
   * Location of the build output (usually 'target')
   * 
   * @parameter expression="${project.build.directory}"
   * @required
   */
  private File outputDirectory;

  /**
   * Location of the site output. Contents of the site output folder are included in the
   * resulting addon zip in the docs directory.
   * 
   * @parameter expression="${project.build.directory}/site"
   */
  private File addonDocDirectory;

  /**
   * Location of additional sources to include in the addon package.
   * 
   * @parameter expression="${basedir}/src/vaadin/addon"
   */
  private File addonSourceDirectory;

  /**
   * Comma separated list of widgetsets, eg. com.example.MyWidgetset. Refers to
   * the GWT xml file(s) (.gwt.xml)
   * 
   * @parameter
   */
  private String addonWidgetSets;

  /**
   * The maven project being built.
   * 
   * @parameter expression="${project}"
   */
  private MavenProject project;

  public void execute() throws MojoExecutionException {
    Log log = getLog();
    String packaging = project.getPackaging();
    if (!("jar".equals(packaging) || "maven-plugin".equals(packaging))) {
      log.info("No addon is created for project with packaging '" +
          packaging + "' (only 'jar' and 'maven-plugin' is supported for addons)");
      return;
    }

    File addonDirectory = new File(outputDirectory, "vaadin-addon");
    addonDirectory.mkdirs();

    String finalName = project.getBuild().getFinalName();

    File addonContent = new File(addonDirectory, finalName);
    addonContent.mkdirs();

    String vaadinAddon = finalName + "/" + finalName + ".jar";

    log.info("Addon contents added to: " + addonContent);
    StringBuilder sb = new StringBuilder();
    sb.append("Manifest-Version: 1.0\n");
    sb.append("Implementation-Title: " + project.getName() + "\n");
    sb.append("Implementation-Version: " + project.getVersion() + "\n");
    sb.append("Vaadin-Package-Version: 1\n");
    sb.append("Vaadin-Addon: " + vaadinAddon + "\n");
    if(this.addonWidgetSets != null) {
      sb.append("Vaadin-Widgetsets: " + this.addonWidgetSets + "\n");
    }
    
    if(this.project.getLicenses() != null) {
      for (Object license : this.project.getLicenses()) {
        License lic = (License) license;
        String licName = lic.getName();
        sb.append("License-Title: " + licName + "\n");
      }
    }

    File manifest = new File(addonDirectory, "MANIFEST.MF");
    try {
      Manifest mf = new Manifest(new ByteArrayInputStream(sb.toString().getBytes("UTF-8")));
      FileOutputStream mfos = new FileOutputStream(manifest);
      mf.write(mfos);
      mfos.flush();
      mfos.close();
      log.info("Manifest:");
      mf.write(System.out);
    } catch (IOException e) {
      throw new MojoExecutionException("Could not write MANIFEST to file: "
          + manifest.getAbsolutePath(), e);
    }

    String addonFilename = finalName + ".jar";
    copyFile(addonContent, addonFilename);
    String addonJavadoc = finalName + "-javadoc.jar";
    copyFile(addonContent, addonJavadoc);
    String addonSource = finalName + "-sources.jar";
    copyFile(addonContent, addonSource);

    // copy site if available
    
//    File docsDirectory = null;
//    if (addonDocDirectory.exists()) {
//      docsDirectory = new File(addonDirectory, "docs");
//      docsDirectory.mkdirs();
//      log.info("Copying site to " + docsDirectory);
//      try {
//        FileUtils.copyDirectoryStructure(addonDocDirectory, docsDirectory);
//      } catch (IOException e) {
//        throw new MojoExecutionException("Failed to copy site", e);
//      }
//    }

    // copy additional addon contents
    // if (addonSourceDirectory.exists()) {
    // try {
    // FileUtils.copyDirectoryStructure(addonSourceDirectory, addonDirectory);
    // } catch (IOException e) {
    // throw new
    // MojoExecutionException("Failed to copy additional addon contents");
    // }
    // }

    // build the zip
    JarArchiver ja = new JarArchiver();
    try {
      ja.addDirectory(addonContent, finalName + "/");
      log.info("Checking docs from " + addonDocDirectory);
      if(addonDocDirectory != null && addonDocDirectory.exists()) {
        log.info("Including docs from " + addonDocDirectory);
        ja.addDirectory(addonDocDirectory, "docs/");
      }
      if(addonSourceDirectory.exists()) {
        ja.addDirectory(addonSourceDirectory);
      }
      ja.setManifest(manifest);
      File addonZip = new File(addonDirectory, finalName + ".zip");
      if (addonZip.exists()) {
        log.info("Removing pre-existing package " + addonZip.getAbsolutePath());
        addonZip.delete();
      }
      ja.setDestFile(addonZip);
      ja.createArchive();
      log.info("Wrote vaadin addon zip package to: " + addonZip.getAbsolutePath());
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to create addon archive", e);
    }
  }

  private void copyFile(File addonContent, String addonFilename)
      throws MojoExecutionException {
    File jar = new File(outputDirectory, addonFilename);
    if (!jar.exists()) {
      return;
    }
    File addonJar = new File(addonContent, addonFilename);
    try {
      FileUtils.copyFile(jar, addonJar);
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Failed to copy project artifact to addon content directory ("
              + jar.getAbsolutePath() + " => " + addonJar.getAbsolutePath()
              + ")", e);
    }
  }

}
