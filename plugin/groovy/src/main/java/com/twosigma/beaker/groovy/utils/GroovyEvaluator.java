package com.twosigma.beaker.groovy.utils;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import com.twosigma.beaker.autocomplete.ClasspathScanner;
import com.twosigma.beaker.autocomplete.groovy.GroovyAutocomplete;
import com.twosigma.beaker.jvm.classloader.DynamicClassLoader;
import com.twosigma.beaker.jvm.object.SimpleEvaluationObject;

public class GroovyEvaluator {
  protected final String shellId;
  protected List<String> classPath;
  protected List<String> imports;
  protected String outDir;
  protected ClasspathScanner cps;
  protected boolean exit;
  protected boolean updateLoader;
  protected final ThreadGroup myThreadGroup;
  protected workerThread myWorker;
  protected GroovyAutocomplete gac;

  protected class jobDescriptor {
    String codeToBeExecuted;
    SimpleEvaluationObject outputObject;

    jobDescriptor(String c , SimpleEvaluationObject o) {
      codeToBeExecuted = c;
      outputObject = o;
    }
  }

  protected final Semaphore syncObject = new Semaphore(0, true);
  protected final ConcurrentLinkedQueue<jobDescriptor> jobQueue = new ConcurrentLinkedQueue<jobDescriptor>();

  public GroovyEvaluator(String id) {
    shellId = id;
    cps = new ClasspathScanner();
    gac = new GroovyAutocomplete(cps);
    classPath = new ArrayList<String>();
    imports = new ArrayList<String>();
    exit = false;
    updateLoader = false;
    myThreadGroup = new ThreadGroup("tg"+shellId);
    startWorker();
  }

  protected void startWorker() {
    myWorker = new workerThread(myThreadGroup);
    myWorker.start();
  }

  public String getShellId() { return shellId; }

  public void killAllThreads() {
    cancelExecution();
  }

  public void cancelExecution() {
    myThreadGroup.interrupt();
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) { }
    myThreadGroup.interrupt();
  }

  public void resetEnvironment() {
    cancelExecution();

    String cpp = "";
    for(String pt : classPath) {
      cpp += pt;
      cpp += File.pathSeparator;
    }
    cpp += File.pathSeparator;
    cpp += System.getProperty("java.class.path");
    cps = new ClasspathScanner(cpp);
    gac = new GroovyAutocomplete(cps);
    
    for(String st : imports)
      gac.addImport(st);

    updateLoader=true;
    syncObject.release();
  } 

  public void exit() {
    exit = true;
    cancelExecution();
  }

  public void setShellOptions(String cp, String in, String od) throws IOException {
    if(cp.isEmpty())
      classPath.clear();
    else
      classPath = Arrays.asList(cp.split("[\\s]+"));
    if (in.isEmpty())
      imports.clear();
    else
      imports = Arrays.asList(in.split("\\s+"));
    outDir = od;
    if(outDir!=null && !outDir.isEmpty()) {
      outDir = outDir.replace("$BEAKERDIR",System.getenv("beaker_tmp_dir"));
      try { (new File(outDir)).mkdirs(); } catch (Exception e) { }
    }
    resetEnvironment();
  }
  
  public void evaluate(SimpleEvaluationObject seo, String code) {
    // send job to thread
    jobQueue.add(new jobDescriptor(code,seo));
    syncObject.release();
  }

  public List<String> autocomplete(String code, int caretPosition) {    
    return gac.doAutocomplete(code, caretPosition);
  }

  protected class workerThread extends Thread {
    protected DynamicClassLoader loader = null;
    protected GroovyShell shell;

    public workerThread(ThreadGroup tg) {
      super(tg, "worker");
    }
    
    /*
     * This thread performs all the evaluation
     */
    
    public void run() {
      jobDescriptor j = null;
      
      while(!exit) {
        try {
          // wait for work
          syncObject.acquire();
          
          // check if we must create or update class loader
          if(updateLoader) {
            shell = null;
          }
          
          // get next job descriptor
          j = jobQueue.poll();
          if(j==null)
            continue;

          if (shell==null) {
            updateLoader=false;
            newEvaluator();
          }
        
          if(loader!=null)
            loader.clearCache();

          j.outputObject.started();
          Object result;
          result = shell.evaluate(j.codeToBeExecuted);
          j.outputObject.finished(result);
        } catch(Throwable e) {
          if(j!=null && j.outputObject != null) {
            if (e instanceof InterruptedException || e instanceof InvocationTargetException) {
              j.outputObject.error("... cancelled!");
            } else {
              j.outputObject.error(e.getMessage());
            }          
          }
        }
      }
    }
      
    protected ClassLoader newClassLoader() throws MalformedURLException
    {
      URL[] urls = {};
      if (!classPath.isEmpty()) {
        urls = new URL[classPath.size()];
        for (int i = 0; i < classPath.size(); i++) {
          System.out.println("url is '"+classPath.get(i)+"'");
          urls[i] = new URL("file://" + classPath.get(i));
        }
      }
      loader = null;
      ClassLoader cl;
      if(outDir!=null && !outDir.isEmpty()) {
        loader = new DynamicClassLoader(outDir);
        loader.addAll(Arrays.asList(urls));
        cl = loader.getLoader();
      } else
        cl = new URLClassLoader(urls);
      return cl;
    }

    protected void newEvaluator() throws MalformedURLException
    {
      ImportCustomizer icz = new ImportCustomizer();

      if (!imports.isEmpty()) {
        for (int i = 0; i < imports.size(); i++) {
          // should trim too
          if (imports.get(i).endsWith(".*")) {
            icz.addStarImports(imports.get(i).substring(0, imports.get(i).length() - 2));
          } else {
            icz.addImports(imports.get(i));
          }
        }
      }
      CompilerConfiguration config = new CompilerConfiguration().addCompilationCustomizers(icz);
      shell = new GroovyShell(newClassLoader(), new Binding(), config);
    }
  }

}

