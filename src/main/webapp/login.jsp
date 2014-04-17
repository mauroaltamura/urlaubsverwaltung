<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@taglib prefix="joda" uri="http://www.joda.org/joda/time/tags" %>
<%@taglib prefix="form" uri="http://www.springframework.org/tags/form" %>


<!DOCTYPE html>
<html>

    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Login</title>
        <link rel="shortcut icon" type="image/x-icon" href="<spring:url value='/favicon.ico?' />" />
        <link rel="stylesheet" type="text/css" href="<spring:url value='/css/fluid_grid.css' />" />
        <link rel="stylesheet" type="text/css" href="<spring:url value='/css/bootstrap.css' />" />
        <link rel="stylesheet" type="text/css" href="<spring:url value='/css/main.css' />" />
        <link rel="stylesheet" type="text/css" href="<spring:url value='/css/login.css' />" />
        <script src="<spring:url value='/jquery/js/jquery-1.9.1.js' />" type="text/javascript" ></script>
        <script type="text/javascript">
            $(document).ready(function() {
                    
                var url = document.URL;
            
                if(url.indexOf("login_error") != -1) {
                    $('#login-error').show('drop', {direction: "up"}); 
                } 
            });
        </script>
    </head>

    <body>


        <div class="navbar navbar-inverse navbar-fixed-top">
            <div class="navbar-inner">
                <div class="container_12">
                    <div class="grid_12">
                        <a class="brand" href="#">Urlaubsverwaltung</a>
                        <div class="nav-collapse collapse">
                            <ul class="nav">
                                <li>
                                    <a title="Version ${project.version} - Commit ${git.commit.id.abbrev}" href="#">Version ${project.version}</a>
                                </li>
                            </ul>
                        </div>    
                    </div>
                </div>
            </div>
        </div>

        <div class="container">

            <form class="form-signin" method="post" action="j_spring_security_check">
                <label for="j_username">Username</label>
                <input class="input-block-level" type="text" name="j_username" id="j_username" autofocus="autofocus">

                <label for="j_password">Passwort</label>
                <input class="input-block-level" type="password" name="j_password" id="j_password">
                
                <button class="btn btn-large btn-primary" type="submit">Login</button>

                <div id="login-error" style="display:none">
                    Der eingegebene Nutzername oder das Passwort ist falsch.
                </div>
            </form>

        </div>

    </body>

</html>
