pipeline {
    agent any

    environment {
        APP_NAME       = 'obresec_web_admin'
        WILDFLY_DEPLOY = 'C:\\wildfly-18.0.1.Final\\standalone\\deployments'
        WILDFLY_HOME   = 'C:\\wildfly-18.0.1.Final'
        PYTHON_HOME = 'C:\\Tools\\Python312'
        PYTHON_EXE = 'C:\\Tools\\Python312\\python.exe'
        WAR_NAME = '${APP_NAME}.war'

        TARGET_JAR =
            'rewrite-servlet-3.4.2.Final.jar'

        TARGET_ISOBIT_JAR =
            'isobit.jar'

        PATCH_SCRIPT =
            'D:\\wildfly\\bin\\patch_war.py'

        BACKUP_ROOT =
            'D:\\backup_gra_web'
    }

    stages {
        stage('Check Environment') {
            steps {
                bat '''
                    echo ==============================
                    echo PYTHON
                    echo ==============================

                    SET PATH=%PYTHON_HOME%;%PYTHON_HOME%\\Scripts;%PATH%

                    python --version
                    python -m pip --version

                '''
            }
        }
        /*
         * Extraemos el WAR solamente para disponer de
         * WEB-INF/lib como classpath durante javac.
         *
         * patch_war.py volvera a trabajar sobre el WAR
         * original de forma independiente.
         */
        stage('PREPARE') {
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo PREPARE
                    echo ========================================

                    echo.
                    echo ===== WAR ORIGINAL =====

                    if not exist "%WILDFLY_DEPLOY%\\%WAR_NAME%" (
                        echo ERROR: No existe:
                        echo %WILDFLY_DEPLOY%\\%WAR_NAME%
                        exit /b 1
                    )

                    dir "%WILDFLY_DEPLOY%\\%WAR_NAME%"

                    echo.
                    echo ===== PATCH SCRIPT =====

                    if not exist "%PATCH_SCRIPT%" (
                        echo ERROR: No existe:
                        echo %PATCH_SCRIPT%
                        exit /b 1
                    )

                    echo %PATCH_SCRIPT%

                    echo.
                    echo ===== PYTHON =====

if not exist "%PYTHON_EXE%" (
    echo ERROR: Python no encontrado:
    echo %PYTHON_EXE%
    exit /b 1
)

"%PYTHON_EXE%" --version

if errorlevel 1 (
    echo ERROR: Python no funciona
    exit /b 1
)

                    echo.
                    echo ===== JAVA =====

                    where java
                    where javac
                    where jar

                    java -version
                    javac -version

                    echo.
                    echo ===== CLEAN =====

                    if exist build (
                        rmdir /S /Q build
                    )

                    if exist war_tmp (
                        rmdir /S /Q war_tmp
                    )

                    if exist patched (
                        rmdir /S /Q patched
                    )

                    if exist verify_tmp (
                        rmdir /S /Q verify_tmp
                    )

                    mkdir build
                    mkdir build\\classes
                    mkdir war_tmp
                    mkdir patched

                    echo.
                    echo ========================================
                    echo EXTRACT ORIGINAL WAR FOR CLASSPATH
                    echo ========================================

                    cd war_tmp

                    jar -xf "%WILDFLY_DEPLOY%\\%WAR_NAME%"

                    if errorlevel 1 (
                        echo ERROR: No se pudo extraer WAR
                        exit /b 1
                    )

                    cd ..

                    echo.
                    echo ===== WEB-INF/lib =====

                    if not exist "war_tmp\\WEB-INF\\lib" (
                        echo ERROR: El WAR no contiene WEB-INF\\lib
                        exit /b 1
                    )

                    if not exist "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" (
                        echo ERROR: No existe %TARGET_JAR%
                        exit /b 1
                    )

                    if not exist "war_tmp\\WEB-INF\\lib\\%TARGET_ISOBIT_JAR%" (
                        echo ERROR: No existe %TARGET_ISOBIT_JAR%
                        exit /b 1
                    )

                    echo.
                    echo PREPARE OK
                '''
            }
        }


        /*
         * Compilamos TODOS los fuentes del repo.
         *
         * Esto NO significa que todos vayan al WAR.
         * Solamente quedan disponibles en build/classes.
         *
         * patch_war.py decide cuales entran realmente.
         */
        stage('COMPILE ALL') {
    tools {
        jdk 'JDK 17'
    }

    steps {
        bat '''
            @echo off
            setlocal EnableDelayedExpansion

            echo ========================================
            echo COMPILE ALL JAVA SOURCES
            echo ========================================

            if not exist src (
                echo ERROR: No existe directorio src
                exit /b 1
            )

            if exist build\\sources.txt (
                del /F /Q build\\sources.txt
            )

            echo.
            echo Generando lista de fuentes...

            powershell -NoProfile -Command ^
              "Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object { '\\"' + ($_.FullName -replace '\\\\','/') + '\\"' } | Set-Content -Encoding ASCII build\\sources.txt"

            if errorlevel 1 (
                echo ERROR: No se pudo generar sources.txt
                exit /b 1
            )

            if not exist build\\sources.txt (
                echo ERROR: No se encontraron fuentes Java
                exit /b 1
            )

            echo.
            echo ===== SOURCES =====

            for /F %%C in ('type build\\sources.txt ^| find /C /V ""') do (
                set SOURCE_COUNT=%%C
            )

            echo Total fuentes: !SOURCE_COUNT!

            if "!SOURCE_COUNT!"=="0" (
                echo ERROR: No hay fuentes Java
                exit /b 1
            )

echo.
echo ========================================
echo JAVAC
echo ========================================

dir /S /B "C:/wildfly-18.0.1.Final/modules/system/layers/base/*el*.jar"
echo ----------

javac ^
  --release 8 ^
  -cp "war_tmp\\WEB-INF\\lib\\*;lib\\*;%WILDFLY_HOME%\\modules\\system\\layers\\base\\javax\\json\\api\\main\\jakarta.json-api-1.1.6.jar;%WILDFLY_HOME%\\modules\\system\\layers\\base\\javax\\enterprise\\api\\main\\*;%WILDFLY_HOME%\\modules\\system\\layers\\base\\javax\\faces\\api\\main\\*;%WILDFLY_HOME%\\modules\\system\\layers\\base\\javax\\inject\\api\\main\\*;%WILDFLY_HOME%\\modules\\system\\layers\\base\\javax\\el\\api\\main\\jboss-el-api_3.0_spec-2.0.0.Final.jar;%WILDFLY_HOME%\\modules\\system\\layers\\base\\javax\\servlet\\api\\main\\*" ^
  -sourcepath src ^
  -d build\\classes ^
  @build\\sources.txt

            if errorlevel 1 (
                echo ERROR: Fallo compilacion Java
                exit /b 1
            )

            echo.
            echo ========================================
            echo CLASSES GENERADAS
            echo ========================================

            for /F %%C in ('dir /S /B build\\classes\\*.class 2^>nul ^| find /C /V ""') do (
                set CLASS_COUNT=%%C
            )

            echo Total classes: !CLASS_COUNT!

            echo.
            echo ===== RewriteFilter =====

            if not exist "build\\classes\\org\\ocpsoft\\rewrite\\servlet\\RewriteFilter.class" (
                echo ERROR: RewriteFilter.class no fue generado
                exit /b 1
            )

            dir /B ^
              "build\\classes\\org\\ocpsoft\\rewrite\\servlet\\RewriteFilter*.class"

            echo.
            echo COMPILE OK

            endlocal
        '''
    }
}


        /*
         * Aqui ocurre el patch quirurgico.
         *
         * Aunque build/classes tenga cientos de clases,
         * solamente se insertan:
         *
         * RewriteFilter.class + RewriteFilter$*.class
         * UserController.class + UserController$*.class
         */
        stage('PATCH WAR') {
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo SURGICAL WAR PATCH
                    echo ========================================

"%PYTHON_EXE%" "%PATCH_SCRIPT%" ^
  --war "%WILDFLY_DEPLOY%\\%WAR_NAME%" ^
  --classes "build\\classes" ^
  --output "patched\\%WAR_NAME%" ^
  --patch "%TARGET_JAR%=org.ocpsoft.rewrite.servlet.RewriteFilter"

                    if errorlevel 1 (
                        echo ERROR: Fallo patch_war.py
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo WAR PATCHED
                    echo ========================================

                    if not exist "patched\\%WAR_NAME%" (
                        echo ERROR: No existe patched\\%WAR_NAME%
                        exit /b 1
                    )

                    dir "patched\\%WAR_NAME%"
                '''
            }
        }


        /*
         * patch_war.py ya verifica internamente.
         *
         * Hacemos una segunda verificacion independiente
         * para que Jenkins compruebe tambien el resultado.
         */
        stage('VERIFY PATCHED WAR') {
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo VERIFY PATCHED WAR
                    echo ========================================

                    if exist verify_tmp (
                        rmdir /S /Q verify_tmp
                    )

                    mkdir verify_tmp

                    cd verify_tmp

                    jar -xf "..\\patched\\%WAR_NAME%"

                    if errorlevel 1 (
                        echo ERROR: No se pudo extraer WAR parcheado
                        exit /b 1
                    )

                    cd ..

                    echo.
                    echo ===== Rewrite JAR =====

                    if not exist "verify_tmp\\WEB-INF\\lib\\%TARGET_JAR%" (
                        echo ERROR: No existe %TARGET_JAR%
                        exit /b 1
                    )

                    jar -tf "verify_tmp\\WEB-INF\\lib\\%TARGET_JAR%" ^
                      | findstr /I /C:"org/ocpsoft/rewrite/servlet/RewriteFilter.class"

                    if errorlevel 1 (
                        echo ERROR: RewriteFilter.class no encontrado
                        exit /b 1
                    )

                    echo RewriteFilter OK

                    echo.
                    echo ========================================
                    echo PATCHED WAR VERIFIED
                    echo ========================================
                '''
            }
        }


        stage('BACKUP ORIGINAL AND PATCHED') {
            steps {
                bat '''
                    @echo off
                    setlocal EnableDelayedExpansion

                    echo ========================================
                    echo BACKUP
                    echo ========================================

                    if not exist "%BACKUP_ROOT%" (
                        mkdir "%BACKUP_ROOT%"
                    )

                    for /F %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do (
                        set "TIMESTAMP=%%I"
                    )

                    set "BACKUP_DIR=%BACKUP_ROOT%\\!TIMESTAMP!"

                    mkdir "!BACKUP_DIR!"

                    if errorlevel 1 (
                        echo ERROR: No se pudo crear:
                        echo !BACKUP_DIR!
                        exit /b 1
                    )

                    echo.
                    echo ===== ORIGINAL =====

                    copy /Y ^
                      "%WILDFLY_DEPLOY%\\%WAR_NAME%" ^
                      "!BACKUP_DIR!\\${APP_NAME}_original.war"

                    if errorlevel 1 (
                        echo ERROR: Fallo backup original
                        exit /b 1
                    )

                    echo.
                    echo ===== PATCHED =====

                    copy /Y ^
                      "patched\\%WAR_NAME%" ^
                      "!BACKUP_DIR!\\${APP_NAME}_patched.war"

                    if errorlevel 1 (
                        echo ERROR: Fallo backup patched
                        exit /b 1
                    )

                    echo.
                    echo ===== RESULT =====

                    dir "!BACKUP_DIR!"

                    echo.
                    echo Backup:
                    echo !BACKUP_DIR!

                    endlocal
                '''
            }
        }


        stage('DEPLOY') {

            when {
                expression { true }
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo DEPLOY
                    echo ========================================

                    if not exist "patched\\%WAR_NAME%" (
                        echo ERROR: WAR parcheado no existe
                        exit /b 1
                    )

                    copy /Y ^
                      "patched\\%WAR_NAME%" ^
                      "%WILDFLY_DEPLOY%\\%WAR_NAME%"

                    if errorlevel 1 (
                        echo ERROR: Fallo deploy
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo DEPLOY COMPLETADO
                    echo ========================================

                    dir "%WILDFLY_DEPLOY%\\%WAR_NAME%"
                '''
            }
        }
    }


    post {

        success {
            echo 'Compilacion completa OK.'
            echo 'RewriteFilter parcheado quirurgicamente.'
            echo 'WAR verificado, respaldado y desplegado.'
        }

        failure {
            echo 'ERROR durante compilacion/patch/deploy.'
            echo 'Revisar backup y logs antes de otro despliegue.'
        }
    }
}