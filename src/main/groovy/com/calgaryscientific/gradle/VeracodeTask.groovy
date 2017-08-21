/*******************************************************************************
 * MIT License
 *
 * Copyright (c) 2017 Calgary Scientific Incorporated
 *
 * Copyright (c) 2013-2014 kctang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/

package com.calgaryscientific.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GFileUtils
import com.veracode.apiwrapper.wrappers.UploadAPIWrapper
import com.veracode.apiwrapper.wrappers.ResultsAPIWrapper

abstract class VeracodeTask extends DefaultTask {
    final static def validArguments = [
            'app_id'           : '123',
            'sandbox_id'       : '123',
            'build_id'         : '123',
            'build_version'    : 'xxx',
            'dir'              : 'xxx',
            'force'            : 'force',
            'fileId'           : '123',
            'mode'             : 'action|actionSummary|verbose',
            'maxUploadAttempts': '123',
            'fileId'           : 'xxx',
            'build_id1'        : '123',
            'build_id2'        : '123'
    ]

    List<String> requiredArguments = []
    List<String> optionalArguments = []

    VeracodeTask() {
        group = 'Veracode'
    }

    abstract void run()

    @TaskAction
    final def vExecute() { if (hasRequiredArguments()) run() }

    // === utility methods ===
    protected static String correctUsage(String taskName,
                                         List<String> requiredArguments,
                                         List<String> optionalArguments) {
        StringBuilder sb = new StringBuilder("Missing required arguments: gradle ${taskName}")
        requiredArguments.each() { arg ->
            sb.append(" -P${arg}=${validArguments.get(arg)}")
        }
        optionalArguments.each() { arg ->
            sb.append(" [-P${arg}=${validArguments.get(arg)}]")
        }
        sb.toString()
    }

    protected boolean hasRequiredArguments() {
        boolean hasRequiredArguments = true
        requiredArguments.each() { arg ->
            hasRequiredArguments &= getProject().hasProperty(arg)
        }
        if (!hasRequiredArguments) {
            fail(correctUsage(this.name, this.requiredArguments, this.optionalArguments))
        }
        return hasRequiredArguments
    }

    protected boolean useAPICredentials() {
        if (project.veracodeCredentials.username != "" &&
                project.veracodeCredentials.password != "") {
            return false
        }
        return true
    }

    protected UploadAPIWrapper uploadAPI() {
        UploadAPIWrapper api = new UploadAPIWrapper()
        if (useAPICredentials()) {
            api.setUpApiCredentials("${project.veracodeCredentials.id}", "${project.veracodeCredentials.key}")
        } else {
            api.setUpCredentials("${project.veracodeCredentials.username}", "${project.veracodeCredentials.password}")
        }
        return api
    }

    protected ResultsAPIWrapper resultsAPI() {
        ResultsAPIWrapper api = new ResultsAPIWrapper()
        if (useAPICredentials()) {
            api.setUpApiCredentials("${project.veracodeCredentials.id}", "${project.veracodeCredentials.key}")
        } else {
            api.setUpCredentials("${project.veracodeCredentials.username}", "${project.veracodeCredentials.password}")
        }
        return api
    }

    protected Node writeXml(String filename, String content) {
        File outputFile = new File("${project.buildDir}/veracode", filename)
        GFileUtils.writeFile(content, outputFile)
        Node xml = new XmlParser().parseText(content)
        if (xml.name() == 'error') {
            fail("ERROR: ${xml.text()}\nSee ${filename} for details!")
        }
        xml
    }

    protected Node writeXml(String content) {
        GFileUtils.writeFile(content, outputFile)
        Node xml = new XmlParser().parseText(content)
        if (xml.name() == 'error') {
            fail("ERROR: ${xml.text()}\nSee ${filename} for details!")
        }
        xml
    }

    protected def readXml(String filename) {
        new XmlParser().parseText(GFileUtils.readFile(new File("${project.buildDir}/veracode", filename)))
    }

    protected List readListFromFile(File file) {
        def set = new HashSet<Set>();
        file.eachLine { line ->
            if (set.contains(line)) {
                println "ERROR: duplicate line: [$line]"
            }
            set.add(line)
        }
        return new ArrayList<String>(set)
    }

    protected fail(String msg) {
        throw new GradleException(msg)
    }
}