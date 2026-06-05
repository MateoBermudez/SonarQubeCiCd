package com.mateo;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.*;

@WebServlet("/hello")
public class HelloServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html lang=\"es\"><body>");
            out.println("<h1>Hola desde Mateo's App!</h1>");
            out.println("<p>Desplegado con Jenkins + Tomcat</p>");
            out.println("</body></html>");
        }
    }
}