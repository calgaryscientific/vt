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

import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import groovy.transform.CompileStatic

@CompileStatic
class VeracodeUploadFileSandboxTask extends VeracodeTask {
    static final String NAME = 'veracodeSandboxUploadFile'
    String maxUploadAttempts
    String waitTimeBetweenAttempts

    VeracodeUploadFileSandboxTask() {
        group = 'Veracode Sandbox'
        description = "Uploads all files defined in 'sandboxFilesToUpload' to Veracode based on the given 'app_id' and 'sandbox_id'"
        requiredArguments << 'app_id' << 'sandbox_id'
        optionalArguments << 'maxUploadAttempts' << 'waitTimeBetweenAttempts'
        app_id = project.findProperty("app_id")
        sandbox_id = project.findProperty("sandbox_id")
        maxUploadAttempts = project.findProperty("maxUploadAttempts")
        waitTimeBetweenAttempts = project.findProperty("waitTimeBetweenAttempts")
        defaultOutputFile = new File("${project.buildDir}/veracode", "filelist-${app_id}-${sandbox_id}-latest.xml")
    }

    @OutputFile
    File getOutputFile() {
        return defaultOutputFile
    }

    @InputFiles
    Set<File> getFileSet() {
        veracodeSetup = project.findProperty("veracodeSetup") as VeracodeSetup
        return veracodeSetup.sandboxFilesToUpload
    }

    void run() {
        Integer maxTries = Integer.parseInt((this.maxUploadAttempts != null) ? this.maxUploadAttempts : '10')
        Integer waitTime = Integer.parseInt((this.waitTimeBetweenAttempts != null) ? this.waitTimeBetweenAttempts : '5000')
        getFileSet().each { file ->
            VeracodeUploadFile.uploadFile(file, maxTries, waitTime, veracodeAPI, getOutputFile())
        }
        println "results file: ${getOutputFile()}"
    }
}