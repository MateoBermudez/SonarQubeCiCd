package com.mateo;

import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.*;

@WebServlet("/hello")
public class HelloServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("text/html;charset=UTF-8");
        try {
            String name = req.getParameter("name");
            String upper = name.toUpperCase();

            PrintWriter out = resp.getWriter();
            out.println("<html lang=\"es\"><body>");
            out.println("<h1>Hola " + upper + "!</h1>");
            out.println("<p>Desplegado con Jenkins + Tomcat</p>");
            out.println("</body></html>");
        } catch (IOException e) {
            // Ignored
        }
    }
}