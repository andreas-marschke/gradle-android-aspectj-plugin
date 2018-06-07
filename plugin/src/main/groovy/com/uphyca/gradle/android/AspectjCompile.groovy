package com.uphyca.gradle.android


import java.io.File;

import org.aspectj.bridge.AbortException;
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.IMessage.Kind;
import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.MessageHandler
import org.aspectj.bridge.MessageUtil;
import org.aspectj.bridge.SourceLocation;

import org.aspectj.tools.ajc.Main

import org.aspectj.util.FileUtil;
import org.aspectj.util.LangUtil;

import org.gradle.api.GradleException

import org.gradle.api.file.FileCollection

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

import org.gradle.api.tasks.compile.AbstractCompile

class AspectjCompile extends AbstractCompile {

    private String encoding
    private FileCollection inpath
    private FileCollection aspectpath
    private String bootclasspath

//    @Override
    @TaskAction
    protected void compile() {

        final def log = project.logger

        //http://www.eclipse.org/aspectj/doc/released/devguide/ajc-ref.html
        //
        // -sourceRoots:
        //  Find and build all .java or .aj source files under any directory listed in DirPaths. DirPaths, like classpath, is a single argument containing a list of paths to directories, delimited by the platform- specific classpath delimiter. Required by -incremental.
        // -inpath:
        //  Accept as source bytecode any .class files in the .jar files or directories on Path. The output will include these classes, possibly as woven with any applicable aspects. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -classpath:
        //  Specify where to find user class files. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -aspectPath:
        //  Weave binary aspects from jar files and directories on path into all sources. The aspects should have been output by the same version of the compiler. When running the output classes, the run classpath should contain all aspectpath entries. Path, like classpath, is a single argument containing a list of paths to jar files, delimited by the platform- specific classpath delimiter.
        // -bootclasspath:
        //  Override location of VM's bootclasspath for purposes of evaluating types when compiling. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -d:
        //  Specify where to place generated .class files. If not specified, Directory defaults to the current working dir.
        // -preserveAllLocals:
        //  Preserve all local variables during code generation (to facilitate debugging).

        def args = [
                "-showWeaveInfo",
                "-encoding", getEncoding(),
                "-source", getSourceCompatibility(),
                "-target", getTargetCompatibility(),
                "-d", destinationDir.absolutePath,
                "-classpath", classpath.asPath,
                "-bootclasspath", bootclasspath,
                "-sourceroots", sourceRoots.join(File.pathSeparator)
        ]
        if (!getInpath().isEmpty()) {
            args << '-inpath'
            args << getInpath().asPath
        }
        if (!getAspectpath().isEmpty()) {
            args << '-aspectpath'
            args << getAspectpath().asPath
        }

        log.debug "ajc args: " + Arrays.toString(args as String[])

        MessageHandler handler = new MessageHandler(true);
        new Main().run(args as String[], handler);
        GradleException ex = null;
        for (IMessage message : handler.getMessages(null, true)) {
            switch (message.getKind()) {
                case IMessage.ABORT:
                case IMessage.ERROR:
                case IMessage.FAIL:
                    log.error renderMessage(message), message.thrown
                    ex = new GradleException(renderMessage(message), message.thrown)
                    break;
                case IMessage.WARNING:
                    log.warn renderMessage(message), message.thrown
                    break;
                case IMessage.INFO:
                    log.info renderMessage(message), message.thrown
                    break;
                case IMessage.DEBUG:
                    log.debug renderMessage(message), message.thrown
                    break;
            }
            
        }
        if (ex != null) {
            throw ex;
        }
    }

    // XXX: This cde was stolen from AspectJ Master branch in Main, it serves as a better means to log compilation exceptions
    /**
     * Render message differently. If abort, then prefix stack trace with feedback request. If the actual message is empty, then
     * use toString on the whole. Prefix message part with file:line; If it has context, suffix message with context.
     * 
     * @param message the IMessage to render
     * @return String rendering IMessage (never null)
     */
    String renderMessage(IMessage message) {
	// IMessage.Kind kind = message.getKind();
        
	StringBuffer sb = new StringBuffer();
	String text = message.getMessage();
	if (text.equals(AbortException.NO_MESSAGE_TEXT)) {
	    text = null;
	}
	boolean toString = (LangUtil.isEmpty(text));
	if (toString) {
	    text = message.toString();
	}
	ISourceLocation loc = message.getSourceLocation();
	String context = null;
	if (null != loc) {
	    File file = loc.getSourceFile();
	    if (null != file) {
		String name = file.getName();
		if (!toString || (-1 == text.indexOf(name))) {
		    sb.append(FileUtil.getBestPath(file));
		    if (loc.getLine() > 0) {
			sb.append(":" + loc.getLine());
		    }
		    int col = loc.getColumn();
		    if (0 < col) {
			sb.append(":" + col);
		    }
		    sb.append(" ");
		}
	    }
	    context = loc.getContext();
	}

	// per Wes' suggestion on dev...
			if (message.getKind() == IMessage.ERROR) {
	    sb.append("[error] ");
	} else if (message.getKind() == IMessage.WARNING) {
	    sb.append("[warning] ");
	}

	sb.append(text);
	if (null != context) {
	    sb.append(LangUtil.EOL);
	    sb.append(context);
	}

	String details = message.getDetails();
	if (details != null) {
	    sb.append(LangUtil.EOL);
	    sb.append('\t');
	    sb.append(details);
	}
	Throwable thrown = message.getThrown();
	if (null != thrown) {
	    sb.append(LangUtil.EOL);
	    sb.append(Main.renderExceptionForUser(thrown));
	}

	if (message.getExtraSourceLocations().isEmpty()) {
	    return sb.toString();
	} else {
	    return MessageUtil.addExtraSourceLocations(message, sb.toString());
	}
    }

    @Input
    String getEncoding() {
        return encoding
    }

    void setEncoding(String encoding) {
        this.encoding = encoding
    }

    @InputFiles
    FileCollection getInpath() {
        return inpath
    }

    void setInpath(FileCollection inpath) {
        this.inpath = inpath
    }

    @InputFiles
    FileCollection getAspectpath() {
        return aspectpath
    }

    void setAspectpath(FileCollection aspectpath) {
        this.aspectpath = aspectpath
    }

    @Input
    String getBootclasspath() {
        return bootclasspath
    }

    void setBootclasspath(String bootclasspath) {
        this.bootclasspath = bootclasspath
    }

    File[] getSourceRoots() {
        def sourceRoots = []
        source.sourceCollections.each {
            it.asFileTrees.each {
                sourceRoots << it.dir
            }
        }
        return sourceRoots
    }
}
