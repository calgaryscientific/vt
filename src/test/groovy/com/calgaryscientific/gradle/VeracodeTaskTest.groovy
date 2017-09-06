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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Specification
import org.gradle.testkit.runner.GradleRunner
import static org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.gradle.testfixtures.ProjectBuilder

class VeracodeTaskTest extends Specification {
    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    Boolean debug = true
    PrintStream stdout = System.out

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def executeTask(String... tasks) {
        GradleRunner.create()
                .withDebug(debug)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments(tasks)
                .build()
    }

    def mockSystemOut() {
        def os = new ByteArrayOutputStream()
        System.out = new PrintStream(os)
        return os
    }

    def getSystemOut(def os) {
        def array = os.toByteArray()
        def is = new ByteArrayInputStream(array)
        return is
    }

    def restoreStdout() {
        System.out = stdout
    }

    def 'Test Task Existence'() {
        when:
        def project = new ProjectBuilder().build()
        project.plugins.apply('com.calgaryscientific.gradle.veracode')
        List<String> taskList = [
                'veracodeBeginPreScan',
                'veracodeBeginScan',
                'veracodeCreateBuild',
                'veracodeDeleteBuild',
                'veracodeDetailedReportCSV',
                'veracodeDetailedReport',
                'veracodeGetAppInfo',
                'veracodeGetAppList',
                'veracodeGetBuildInfo',
                'veracodeGetBuildList',
                'veracodeGetFileList',
                'veracodeGetPreScanResults',
                'veracodeRemoveFile',
                'veracodeUploadFile',
        ]

        then:
        for (String taskName : taskList) {
            assert project.tasks.getByName(taskName) != null
        }
    }

    def 'Test build failure when arguments are missing'() {
        given:
        buildFile << """
            plugins {
                id 'com.calgaryscientific.gradle.veracode'
            }
            task getBuildList (type: com.calgaryscientific.gradle.VeracodeGetBuildListTask) {
            }
        """

        when:
        def result = executeTask('getBuildList')

        then:
        def e = thrown(UnexpectedBuildFailure)
        e.toString().contains("Missing required arguments")
    }

    def 'Test error message on missing parameters'() {
        when:
        List<String> requiredArgs = ["app_id"]
        List<String> optionalArgs = ["build_id"]
        String correctUsage = VeracodeTask.correctUsage('VeracodeGetBuildList', requiredArgs, optionalArgs)

        then:
        correctUsage == "Missing required arguments: gradle VeracodeGetBuildList -Papp_id=123 [-Pbuild_id=123]"
    }

    def 'Test veracodeSetup usage'() {
        given:
        buildFile << """
            plugins {
                id 'com.calgaryscientific.gradle.veracode'
            }

            veracodeSetup {
                username = 'user'
                password = 'pass'
                id = 'id'
                key = 'key'
                filesToUpload = fileTree(dir: ".", include: "*").getFiles()
            }
            task verify {
                doLast {
                    assert project.veracodeSetup.username == 'user'
                    assert project.veracodeSetup.password == 'pass'
                    assert project.veracodeSetup.id == 'id'
                    assert project.veracodeSetup.key == 'key'
                    def vc = project.findProperty('veracodeSetup')
                    assert vc.key == 'key'
                    assert vc.filesToUpload == [buildFile] as Set
                    vc.filesToUpload.add(buildFile)
                    assert vc.filesToUpload  == [buildFile, buildFile] as Set
                    vc.filesToUpload.addAll(fileTree(dir: ".", include: "*").getFiles())
                    assert vc.filesToUpload  == [buildFile, buildFile, buildFile] as Set
                }
            }
        """

        when:
        def result = executeTask('verify')

        then:
        result.task(":verify").outcome == SUCCESS
    }

    def 'Test VeracodeUploadFile printFileUploadStatus'() {
        given:
        def os = mockSystemOut()
        String xmlStr = '''
<filelist xmlns="something" xmlns:xsi="something" filelist_version="1.1">
    <file file_id="1" file_md5="d98b6f5ccfce3799e9b60b5d78cc1" file_name="file1" file_status="Uploaded"/>
    <file file_id="2" file_md5="68a7d8468ca51bc46d5b72d485022" file_name="file2" file_status="Uploaded"/>
    <file file_id="3" file_md5="2459464ff4bf78dd6f09695069b52" file_name="file3" file_status="Uploaded"/>
</filelist>
'''
        when:
        XmlParser xmlParser = new XmlParser()
        Node xml = xmlParser.parseText(xmlStr)
        VeracodeUploadFileTask.printFileUploadStatus(xml)
        def is = getSystemOut(os)
        restoreStdout()

        then:
        assert is.readLines() == ['file1=Uploaded', 'file2=Uploaded', 'file3=Uploaded']
    }

    def 'Test veracodeSetup filesToUpload are properly set'() {
        given:
        def project = new ProjectBuilder().build()
        VeracodeSetup vs = new VeracodeSetup()
        vs.filesToUpload = project.fileTree(dir: testProjectDir.root, include: '**/*').getFiles()

        when:
        project.plugins.apply('com.calgaryscientific.gradle.veracode')
        project.veracodeSetup.filesToUpload = vs.filesToUpload

        then:
        VeracodeSetup vsRead = project.findProperty("veracodeSetup") as VeracodeSetup
        Set<File> expected = [buildFile] as Set
        assert vsRead.filesToUpload == expected
        VeracodeUploadFileTask task = project.tasks.getByName("veracodeUploadFile") as VeracodeUploadFileTask
        assert task.veracodeSetup.filesToUpload == vsRead.filesToUpload
        assert task.getFileSet() == expected
        def _ = vsRead.filesToUpload.add(buildFile)
        assert vsRead.filesToUpload == [buildFile, buildFile] as Set
    }

    def 'Test VeracodeUploadFile Task'() {
        given:
        VeracodeAPI veracodeAPIMock = Mock(VeracodeAPI, constructorArgs: ["", "", null, null])
        String xmlStr = '''<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<filelist xmlns="something" xmlns:xsi="something" filelist_version="1.1">
    <file file_id="1" file_md5="d98b6f5ccfce3799e9b60b5d78cc1" file_name="file1" file_status="Uploaded"/>
    <file file_id="2" file_md5="68a7d8468ca51bc46d5b72d485022" file_name="file2" file_status="Uploaded"/>
    <file file_id="3" file_md5="2459464ff4bf78dd6f09695069b52" file_name="file3" file_status="Uploaded"/>
</filelist>
'''
        Project project = new ProjectBuilder().build()
        project.plugins.apply('com.calgaryscientific.gradle.veracode')
        VeracodeSetup vs = new VeracodeSetup()
        vs.filesToUpload = project.fileTree(dir: testProjectDir.root, include: '**/*').getFiles()
        project.veracodeSetup.filesToUpload = vs.filesToUpload
        VeracodeUploadFileTask task = project.tasks.getByName("veracodeUploadFile") as VeracodeUploadFileTask
        task.veracodeAPI = veracodeAPIMock

        when:
        task.run()

        then:
        1 * veracodeAPIMock.uploadFile(_, _) >> {
            return xmlStr
        }
    }

    def 'Test VeracodeUploadFile Task failure'() {
        given:
        VeracodeAPI veracodeAPIMock = Mock(VeracodeAPI, constructorArgs: ["", "", null, null])
        String xmlStr = '''<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<error>Could not upload file</error>
'''
        Project project = new ProjectBuilder().build()
        project.plugins.apply('com.calgaryscientific.gradle.veracode')
        VeracodeSetup vs = new VeracodeSetup()
        vs.filesToUpload = project.fileTree(dir: testProjectDir.root, include: '**/*').getFiles()
        project.veracodeSetup.filesToUpload = vs.filesToUpload
        VeracodeUploadFileTask task = project.tasks.getByName("veracodeUploadFile") as VeracodeUploadFileTask
        task.veracodeAPI = veracodeAPIMock
        task.waitTimeBetweenAttempts = "0"

        when:
        task.run()

        then:
        10 * veracodeAPIMock.uploadFile(_, _) >> {
            return xmlStr
        }
        def e = thrown(GradleException)
        e.toString().contains("ERROR: Could not upload file")
    }
}
