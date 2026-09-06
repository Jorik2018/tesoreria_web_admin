package org.ocpsoft.rewrite.servlet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.ws.rs.core.Response;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.isobit.app.X;
import org.isobit.app.ejb.MenuFacadeLocal;
import org.isobit.app.ejb.SystemFacadeLocal;
import org.isobit.app.ejb.UserFacadeLocal;
import org.isobit.app.jpa.User;
import org.isobit.util.AbstractFacadeLocal;
import org.isobit.util.TestFacadeLocal;
import org.isobit.util.XUtil;
import org.ocpsoft.common.pattern.WeightedComparator;
import org.ocpsoft.common.services.ServiceLoader;
import org.ocpsoft.common.spi.ServiceEnricher;
import org.ocpsoft.common.util.Iterators;
import org.ocpsoft.logging.Logger;
import org.ocpsoft.rewrite.AbstractRewrite;
import org.ocpsoft.rewrite.Version;
import org.ocpsoft.rewrite.config.ConfigurationProvider;
import org.ocpsoft.rewrite.el.spi.ExpressionLanguageProvider;
import org.ocpsoft.rewrite.event.Flow;
import org.ocpsoft.rewrite.event.Rewrite;
import org.ocpsoft.rewrite.servlet.ServletRewriteProvider;
import org.ocpsoft.rewrite.servlet.event.BaseRewrite;
import org.ocpsoft.rewrite.servlet.event.InboundServletRewrite;
import org.ocpsoft.rewrite.servlet.impl.HttpRewriteContextImpl;
import org.ocpsoft.rewrite.servlet.spi.ContextListener;
import org.ocpsoft.rewrite.servlet.spi.InboundRewriteProducer;
import org.ocpsoft.rewrite.servlet.spi.OutboundRewriteProducer;
import org.ocpsoft.rewrite.servlet.spi.RequestCycleWrapper;
import org.ocpsoft.rewrite.servlet.spi.RequestListener;
import org.ocpsoft.rewrite.servlet.spi.RequestParameterProvider;
import org.ocpsoft.rewrite.servlet.spi.RewriteLifecycleListener;
import org.ocpsoft.rewrite.servlet.spi.RewriteResultHandler;
import org.ocpsoft.rewrite.spi.ConfigurationCacheProvider;
import org.ocpsoft.rewrite.spi.InvocationResultHandler;
import org.ocpsoft.rewrite.spi.RewriteProvider;
import org.ocpsoft.rewrite.util.ServiceLogger;

public class RewriteFilter implements Filter {
    private static String DEFAULT_TEMPLATE = "/template.xhtml";

    private static String MASTER_SESSION_ID = "MAIN_TOKEN";

    private static Logger log = Logger.getLogger(RewriteFilter.class);

    private static String FILTER_COUNT_KEY = RewriteFilter.class.getName() + "_FILTER_COUNT";

    private List<RewriteLifecycleListener<Rewrite>> listeners;

    private List<RequestCycleWrapper<ServletRequest, ServletResponse>> wrappers;

    private List<RewriteProvider<ServletContext, Rewrite>> providers;

    private List<RewriteResultHandler> resultHandlers;

    private List<InboundRewriteProducer<ServletRequest, ServletResponse>> inbound;

    private List<OutboundRewriteProducer<ServletRequest, ServletResponse, Object>> outbound;

    private ServletContext servletContext;

    public void init(FilterConfig filterConfig) throws ServletException {
        if (log.isInfoEnabled())
            log.info("RewriteFilter starting up...");
        this.servletContext = filterConfig.getServletContext();
        this.listeners = Iterators.asList((Iterable) ServiceLoader.load(RewriteLifecycleListener.class));
        this.wrappers = Iterators.asList((Iterable) ServiceLoader.load(RequestCycleWrapper.class));
        this.providers = Iterators.asList((Iterable) ServiceLoader.load(RewriteProvider.class));
        this.resultHandlers = Iterators.asList((Iterable) ServiceLoader.load(RewriteResultHandler.class));
        this.inbound = Iterators.asList((Iterable) ServiceLoader.load(InboundRewriteProducer.class));
        this.outbound = Iterators.asList((Iterable) ServiceLoader.load(OutboundRewriteProducer.class));
        Collections.sort(this.listeners,
                (Comparator<? super RewriteLifecycleListener<Rewrite>>) new WeightedComparator());
        Collections.sort(this.wrappers,
                (Comparator<? super RequestCycleWrapper<ServletRequest, ServletResponse>>) new WeightedComparator());
        Collections.sort(this.providers,
                (Comparator<? super RewriteProvider<ServletContext, Rewrite>>) new WeightedComparator());
        Collections.sort(this.resultHandlers, (Comparator<? super RewriteResultHandler>) new WeightedComparator());
        Collections.sort(this.inbound,
                (Comparator<? super InboundRewriteProducer<ServletRequest, ServletResponse>>) new WeightedComparator());
        Collections.sort(this.outbound,
                (Comparator<? super OutboundRewriteProducer<ServletRequest, ServletResponse, Object>>) new WeightedComparator());
        ServiceLogger.logLoadedServices(log, RewriteLifecycleListener.class, this.listeners);
        ServiceLogger.logLoadedServices(log, RequestCycleWrapper.class, this.wrappers);
        ServiceLogger.logLoadedServices(log, RewriteProvider.class, this.providers);
        ServiceLogger.logLoadedServices(log, RewriteResultHandler.class, this.resultHandlers);
        ServiceLogger.logLoadedServices(log, InboundRewriteProducer.class, this.inbound);
        ServiceLogger.logLoadedServices(log, OutboundRewriteProducer.class, this.outbound);
        ServiceLogger.logLoadedServices(log, ContextListener.class,
                Iterators.asList((Iterable) ServiceLoader.load(ContextListener.class)));
        ServiceLogger.logLoadedServices(log, RequestListener.class,
                Iterators.asList((Iterable) ServiceLoader.load(RequestListener.class)));
        ServiceLogger.logLoadedServices(log, RequestParameterProvider.class,
                Iterators.asList((Iterable) ServiceLoader.load(RequestParameterProvider.class)));
        ServiceLogger.logLoadedServices(log, ExpressionLanguageProvider.class,
                Iterators.asList((Iterable) ServiceLoader.load(ExpressionLanguageProvider.class)));
        ServiceLogger.logLoadedServices(log, InvocationResultHandler.class,
                Iterators.asList((Iterable) ServiceLoader.load(InvocationResultHandler.class)));
        ServiceLogger.logLoadedServices(log, ServiceEnricher.class,
                Iterators.asList((Iterable) ServiceLoader.load(ServiceEnricher.class)));
        ServiceLogger.logLoadedServices(log, ConfigurationCacheProvider.class,
                Iterators.asList((Iterable) ServiceLoader.load(ConfigurationCacheProvider.class)));
        List<ConfigurationProvider<?>> configurations = Iterators.asList(
                (Iterable) ServiceLoader.load(ConfigurationProvider.class));
        ServiceLogger.logLoadedServices(log, ConfigurationProvider.class, configurations);
        for (RewriteProvider<ServletContext, Rewrite> provider : this.providers) {
            if (provider instanceof ServletRewriteProvider)
                ((ServletRewriteProvider) provider).init(this.servletContext);
        }
        if ((configurations == null || configurations.isEmpty()) &&
                log.isWarnEnabled())
            log.warn(
                    "No ConfigurationProviders were registered: Rewrite will not be enabled on this application. Did you forget to create a '/META-INF/services/"
                            + ConfigurationProvider.class

                                    .getName()
                            + " file containing the fully qualified name of your provider implementation?");
        if (log.isInfoEnabled())
            log.info(Version.getFullName() + " initialized.");
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!preFilter(request, response, chain))
            return;
        if (request.getAttribute("noload") != null) {
            chain.doFilter(request, response);
            return;
        }
        InboundServletRewrite<ServletRequest, ServletResponse> event = createRewriteEvent(request, response);
        if (event == null) {
            if (log.isWarnEnabled())
                log.warn("No Rewrite event was produced - RewriteFilter disabled on this request.");
            chain.doFilter(request, response);
        } else {
            incrementFilterCount(request);
            if (request.getAttribute("_com.ocpsoft.rewrite.RequestContext") == null) {
                HttpRewriteContextImpl httpRewriteContextImpl = new HttpRewriteContextImpl(this.inbound, this.outbound,
                        this.listeners, this.resultHandlers, this.wrappers, this.providers);
                request.setAttribute("_com.ocpsoft.rewrite.RequestContext", httpRewriteContextImpl);
            }
            for (RewriteLifecycleListener<Rewrite> listener : this.listeners) {
                if (listener.handles(event))
                    listener.beforeInboundLifecycle((Rewrite) event);
            }
            for (RequestCycleWrapper<ServletRequest, ServletResponse> wrapper : this.wrappers) {
                if (wrapper.handles(event)) {
                    event.setRequest(wrapper.wrapRequest(event.getRequest(), event.getResponse(), this.servletContext));
                    event.setResponse(
                            wrapper.wrapResponse(event.getRequest(), event.getResponse(), this.servletContext));
                }
            }
            try {
                rewrite(event);
            } catch (ServletException e) {
                if (getFilterCount(request) == 1)
                    AbstractRewrite.logEvaluatedRules((Rewrite) event, Logger.Level.ERROR);
                decrementFilterCount(request);
                throw e;
            } catch (RuntimeException e) {
                if (getFilterCount(request) == 1)
                    AbstractRewrite.logEvaluatedRules((Rewrite) event, Logger.Level.ERROR);
                decrementFilterCount(request);
                throw e;
            }
            if (!event.getFlow().is((Flow) BaseRewrite.ServletRewriteFlow.ABORT_REQUEST)) {
                if (log.isDebugEnabled())
                    log.debug("RewriteFilter passing control of request to underlying application.");
                if (response.isCommitted() && log.isWarnEnabled())
                    log.warn(
                            "Response has already been committed, and further write operations are not permitted. This may result in an IllegalStateException being triggered by the underlying application. To avoid this situation, consider adding a Rule `.when(Direction.isInbound().and(Response.isCommitted())).perform(Lifecycle.abort())`, or figure out where the response is being incorrectly committed and correct the bug in the offending code.");
                chain.doFilter(event.getRequest(), event.getResponse());
                if (log.isDebugEnabled())
                    log.debug("Control of request returned to RewriteFilter.");
            }
            for (RewriteLifecycleListener<Rewrite> listener : this.listeners) {
                if (listener.handles(event))
                    listener.afterInboundLifecycle((Rewrite) event);
            }
            if (getFilterCount(request) == 1)
                AbstractRewrite.logEvaluatedRules((Rewrite) event, Logger.Level.DEBUG);
            decrementFilterCount(request);
        }
    }

    private Client client = ClientBuilder.newClient();

    private User initSessionFromJwt(String jwt) {
        Integer uid = getUidFromJwt(jwt);

        if (uid == null) {
            return null;
        }

        try {
            UserFacadeLocal userFacade = (UserFacadeLocal) new InitialContext()
                    .lookup("java:module/UserFacade");

            return userFacade.initSession(uid);

        } catch (Exception e) {
            System.out.println(
                    "ERROR iniciando session uid=" + uid);

            e.printStackTrace();

            return null;
        }
    }

    private Integer getUidFromJwt(String token) {
        Response response = null;

        try {
            response = client
                    .target("http://localhost:5055/info")// api/auth
                    .request("application/json")
                    .header("Authorization", "Bearer " + token)
                    .get();
            System.out.println("Bearer " + token);

            if (response.getStatus() != 200) {
                System.out.println(
                        "JWT INVALIDO status=" + response.getStatus());
                return null;
            }

            Map data = response.readEntity(Map.class);

            return Integer.valueOf(
                    XUtil.intValue(data.get("uid")));

        } catch (Exception e) {
            e.printStackTrace();
            return null;

        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public boolean preFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;
            if (request != null) {
                HttpSession session = request.getSession(false);
                String requestURI = request.getRequestURI();
                int traceId = (int) (Math.random() * 900000) + 100000;

                int p = requestURI.indexOf(";jsessionid");
                if (p > -1)
                    requestURI = requestURI.substring(0, p);
                String contextPath = request.getContextPath();
                if (X.CONTEXT_PATH == null)
                    X.CONTEXT_PATH = contextPath;
                request.setAttribute("contextPath", contextPath);
                if (requestURI.endsWith("/"))
                    requestURI = requestURI.substring(0, requestURI.length() - 1);
                if (requestURI.startsWith("/"))
                    requestURI = requestURI.substring(1);
                String[] q = requestURI.split("/");
                q[q.length - 1] = q[q.length - 1].replaceAll(".xhtml", "");
                if (request.getAttribute("#q") == null) {
                    request.setAttribute("#q", q);
                    request.setAttribute("#requestURI", requestURI);
                }
                String URI = requestURI.toLowerCase();
                if (isStaticResource(URI)) {
                    request.setAttribute(X.NO_LOAD, Boolean.valueOf(true));
                    String destinyRequest = request.getParameter("destiny");
                    if (destinyRequest != null)
                        session.setAttribute("_DESTINY", destinyRequest);
                    chain.doFilter(request, (ServletResponse) response);
                    return false;
                }
                if (session == null) {
                    session = request.getSession(true);
                }
                X.setSession(session);
                X.setRequest(request);
                request.setAttribute("_MSG", session.getAttribute("_MSG"));
                session.removeAttribute("_MSG");
                User user = (User) session.getAttribute("_USER");
                if (("logout".equals(q[0]))) {
                    ((UserFacadeLocal) (new InitialContext()).lookup("java:module/UserFacade")).logout();
                    Cookie refreshCookie = new Cookie("refreshToken", "");
                    refreshCookie.setPath("/");
                    refreshCookie.setMaxAge(0);
                    refreshCookie.setHttpOnly(true);
                    refreshCookie.setSecure(true);
                    response.addCookie(refreshCookie);
                    String destiny = request.getParameter("destiny");
                    if (destiny != null && !destiny.trim().isEmpty()) {
                        response.sendRedirect(destiny);
                    } else {
                        response.sendRedirect("/admin");
                    }
                    return false;
                }
                if (request.getAttribute(X.NO_LOAD) != null) {
                    chain.doFilter(request, (ServletResponse) response);
                    return false;
                }

                if (request.getAttribute("URL_ENTER") == null && req.getAttribute(X.NO_LOAD) == null) {
                    request.setAttribute("URL_ENTER", requestURI);
                }
                if (!X.installed) {
                    SystemFacadeLocal systemFacade = lookupSystemFacadeLocal();
                    Map m = systemFacade.getConfig("SYSTEM");
                    if (m == null ||
                            !m.containsKey("installed") ||
                            !(X.installed = systemFacade.hasAdmin())) {
                        if (!requestURI.endsWith("faces/Install.xhtml")) {
                            response.sendRedirect("/faces/Install.xhtml");
                            return false;
                        }
                        try {
                            System.out.println("2do chain.doFilter(req, response);");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (requestURI.contains("/api/") || "api"
                        .equals(q[0]) || (q.length > 1 && "api".equals(q[1]))) {
                    response.addHeader("Access-Control-Allow-Origin", "*");
                    response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD, PUT, POST");
                    chain.doFilter(request, (ServletResponse) response);
                    return false;
                }
                if (request.getAttribute("TEMPLATE") == null) {
                    if (request.getParameter("modal") != null) {
                        request.setAttribute(X.TEMPLATE, "/modal.xhtml");
                    } else {
                        request.setAttribute(X.TEMPLATE, ("admin".equals(q[0])) ? DEFAULT_TEMPLATE : "/nodeTemplate.xhtml");
                    }
                }
                System.out.println(traceId + " 7 " + user + " req.getAttribute(X.NO_LOAD)  =>"
                        + request.getAttribute(X.NO_LOAD)+" requestURI=" + requestURI);

                String jwtRefreshToken = getCookieValue(request, "refreshToken");
                if (user != null
                        || jwtRefreshToken != null
                        || requestURI.startsWith("login")
                        || requestURI.endsWith("/register")
                        || requestURI.startsWith("user/reset/")
                        || requestURI.endsWith("/password")
                        || !requestURI.startsWith("admin")) {
                    {
                        X.DEBUG = true;
                        String destinyRequest = req.getParameter("destiny");
                        if (user != null && !contextPath.equals("")) {// verificar master session valida (mejorar usando
                            String jwtToken = (String) session.getAttribute("jwtToken");
                            System.out.println(traceId + " 8 jwtToken=" + jwtToken + " requestURI=" + requestURI);
                            Integer uid = getUidFromJwt((String)request.getAttribute(jwtToken));// api/auth)
                            if (uid == null || uid <= 0) {
                                System.out.println(traceId + " 9 juid=" + uid);
                            
                                ((UserFacadeLocal) (new InitialContext()).lookup("java:module/UserFacade")).logout();
                                response.sendRedirect("/" + requestURI);
                                return false;
                            }
                        }
                        if (!(user != null && user.getUid() > 0) && !XUtil.isEmpty(jwtRefreshToken)) {// login master
                            String jwtToken = refreshAccessToken(request, jwtRefreshToken);
                            if (!XUtil.isEmpty(jwtToken)) {
                                request.getSession().setAttribute("jwtToken", jwtToken);
                                User loggedUser = initSessionFromJwt(jwtToken);
                                System.err.println("======traceId=" + traceId + " loggedUser = " + loggedUser+" destinyRequest=" + destinyRequest);
                                if (loggedUser != null) {
                                    if (!XUtil.isEmpty(destinyRequest)) {
                                        if (redirectToSlave(
                                                request,
                                                response,
                                                destinyRequest,
                                                loggedUser)) {
                                            return false;
                                        }
                                        response.sendRedirect("/" + destinyRequest);
                                    } else {
                                        //response.sendRedirect("/" + requestURI);
                                        return true;
                                    }
                                    return false;
                                }
                            }
                            // mostrar mensaje de error de login
                            if (!XUtil.isEmpty(destinyRequest)) {
                                response.sendRedirect(
                                        "/login/?destiny=" + destinyRequest);
                            } else {
                                response.sendRedirect("/login");
                            }
                            return false;
                        }

                        if (requestURI.equals("login") && redirectToSlave(
                                        request,
                                        response,
                                        destinyRequest,
                                        user)) {
                            return false;
                        }
                        if (destinyRequest != null) {
                            session.setAttribute("_DESTINY", destinyRequest);
                        }
                        if (request.getAttribute("noload") != null) {
                            System.out.println("no load");
                            return true;
                        }

                        // los esclavos empiezan con ejemplo:/admin/warrant/*
                        if (requestURI.startsWith("admin") || requestURI.startsWith("faces/")) {
                            String access_token = (String) session.getAttribute("jwtToken");
                            System.out.println("traceId=" + traceId + " access_token=" + access_token + " user=" + user
                                    + " checkedAccess=" + req.getAttribute("-checkedAccess"));
                            if (access_token != null) {
                                Object modal = req.getParameter("modal");
                                if (modal != null)
                                    requestURI = requestURI + "?modal";
                                if (user == null) {
                                    /*Object uid = req.getParameter("uid");
                                    if (uid == null) {
                                        ((UserFacadeLocal) (new InitialContext()).lookup("java:module/UserFacade"))
                                                .initSession(Integer.valueOf(XUtil.intValue(access_token)));
                                        response.sendRedirect("/" + requestURI);
                                        return false;
                                    }*/
                                }
                                //response.sendRedirect("/" + requestURI);
                                return true;
                            }
                            if (req.getAttribute("-checkedAccess") == null) {
                                Object o = null;
                                try {
                                    o = ((MenuFacadeLocal) (new InitialContext()).lookup("java:module/MenuFacade"))
                                            .accessMenu(q);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                if (o instanceof Exception) {
                                    ((Exception) o).printStackTrace();
                                    request.setAttribute("MSG", ((Exception) o).getMessage());
                                    req.setAttribute("noload", Boolean.valueOf(true));
                                    request.getRequestDispatcher("/faces/common/Page.xhtml").forward(req, res);
                                }
                                req.setAttribute("-checkedAccess", Boolean.valueOf(true));
                            }
                        } else if (requestURI.endsWith(".xhtml")) {
                            req.setAttribute("noload", Boolean.valueOf(true));
                        } else {
                            req.setAttribute("-checkedAccess", Boolean.valueOf(true));
                        }
                    }
                } else if (req.getAttribute("-checkedAccess") == null) {
                    if ("faces/".equals(requestURI))
                        requestURI = null;
                    String access_token = req.getParameter("access_token");
                    System.out.println("context-path=" + request.getContextPath());
                    String mainSessionId = (String) session.getAttribute(MASTER_SESSION_ID);
                    if (access_token != null && user == null) {
                        String[] tr = access_token.split("[.]");
                        String sessionId = tr[2];
                        System.out.println("Preguntando al main si es valido el id=" + sessionId);
                        int uid = ((Integer) this.client.target("http://localhost:" + X.getRequest().getLocalPort()
                                + "/api/session/logged/" + sessionId).request().get(Integer.class)).intValue();
                        if (uid > -1) {
                            System.out.println("Es valido se inicia session");
                            user = ((UserFacadeLocal) (new InitialContext()).lookup("java:module/UserFacade"))
                                    .initSession(Integer.valueOf(uid));
                            if (user != null) {
                                session.setAttribute(MASTER_SESSION_ID, sessionId);
                                System.out.println("session.getId()=" + session.getId() + " guarda mainSessionId="
                                        + mainSessionId + " USER INICIADO=" + user + " en contextPath=" + contextPath
                                        + " Despues se redirige a /" + requestURI);
                                response.sendRedirect("/" + requestURI);
                                return false;
                            }
                        } else {
                            System.out.println("fallo valido se inicia session " + sessionId);
                        }
                    } else if (user != null) {
                        mainSessionId = (String) session.getAttribute(MASTER_SESSION_ID);
                        int uid = ((Integer) this.client.target("http://localhost:" + X.getRequest().getLocalPort()
                                + "/api/session/logged/" + mainSessionId).request().get(Integer.class)).intValue();
                        if (uid <= 0) {
                            ((UserFacadeLocal) (new InitialContext()).lookup("java:module/UserFacade")).logout();
                            response.sendRedirect("/" + requestURI);
                            return false;
                        }
                    }
                    session.setAttribute("_DESTINY", requestURI);
                    response.sendRedirect("/login?destiny=" + requestURI);
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private String refreshAccessToken(
            HttpServletRequest request,
            String refreshToken) {

        Response response = null;

        try {
            String url = "http://localhost/api/auth/refresh";

            response = client
                    .target(url)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .cookie("refreshToken", refreshToken)
                    .post(Entity.json(Collections.emptyMap()));

            int status = response.getStatus();

            if (status != 200) {

                String body = response.hasEntity()
                        ? response.readEntity(String.class)
                        : "";

                System.out.println(
                        "REFRESH FAILED status="
                                + status
                                + " body="
                                + body);

                return null;
            }

            Map result = response.readEntity(Map.class);

            Object token = result.get("token");

            return token != null
                    ? token.toString()
                    : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;

        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private String getCookieValue(
            HttpServletRequest request,
            String name) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean redirectToSlave(
            HttpServletRequest request,
            HttpServletResponse response,
            String destinyRequest,
            User user) throws IOException {

        if (XUtil.isEmpty(destinyRequest)
                || user == null
                || user.getUid() == null
                || user.getUid().intValue() <= 0) {
            return false;
        }
        response.sendRedirect("/" + destinyRequest);

        return true;
    }

    private Map validateToken(String token) throws Exception {
        String POST_PARAMS = "{\"token\":\"" + token + "\"}";
        System.out.println(POST_PARAMS);
        URL obj = new URL("http://web.regionancash.gob.pe/auth/api/tblusuario/verificartoken");
        HttpURLConnection postConnection = (HttpURLConnection) obj.openConnection();
        postConnection.setRequestMethod("POST");
        postConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        postConnection.setDoOutput(true);
        OutputStream os = postConnection.getOutputStream();
        os.write(POST_PARAMS.getBytes());
        os.flush();
        os.close();
        int responseCode = postConnection.getResponseCode();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(postConnection.getInputStream(), Charset.forName("UTF-8")));
        StringBuffer response = new StringBuffer();
        String inputLine;
        while ((inputLine = in.readLine()) != null)
            response.append(inputLine);
        in.close();
        System.out.println(response);
        HashMap<Object, Object> m = new HashMap<>();
        JsonParser parser = Json.createParser(new ByteArrayInputStream(response.toString().getBytes()));
        String keyName = "";
        while (parser.hasNext()) {
            JsonParser.Event e = parser.next();
            switch (e) {
                case VALUE_NUMBER:
                    m.put(keyName, Integer.valueOf(parser.getInt()));
                case VALUE_STRING:
                    m.put(keyName, parser.getString());
                case VALUE_TRUE:
                    m.put(keyName, Boolean.valueOf(true));
                case VALUE_FALSE:
                    m.put(keyName, Boolean.valueOf(false));
                case KEY_NAME:
                    keyName = parser.getString();
                    switch (keyName) {
                        case "valido":
                            keyName = "valid";
                    }
            }
        }
        return m;
    }

    public InboundServletRewrite<ServletRequest, ServletResponse> createRewriteEvent(ServletRequest request,
            ServletResponse response) {
        for (InboundRewriteProducer<ServletRequest, ServletResponse> producer : this.inbound) {
            InboundServletRewrite<ServletRequest, ServletResponse> event = producer.createInboundRewrite(request,
                    response, this.servletContext);
            if (event != null)
                return event;
        }
        return null;
    }

    private void rewrite(InboundServletRewrite<ServletRequest, ServletResponse> event)
            throws ServletException, IOException {
        int listenerCount = this.listeners.size();
        for (int i = 0; i < listenerCount; i++) {
            RewriteLifecycleListener<Rewrite> listener = this.listeners.get(i);
            if (listener.handles(event))
                listener.beforeInboundRewrite((Rewrite) event);
        }
        int providerCount = this.providers.size();
        int j;
        for (j = 0; j < providerCount; j++) {
            RewriteProvider<ServletContext, Rewrite> provider = this.providers.get(j);
            if (provider.handles(event)) {
                provider.rewrite((Rewrite) event);
                if (event.getFlow().is((Flow) BaseRewrite.ServletRewriteFlow.HANDLED)) {
                    if (log.isDebugEnabled())
                        log.debug("Event flow marked as HANDLED. No further processing will occur.");
                    break;
                }
            }
        }
        for (j = 0; j < listenerCount; j++) {
            RewriteLifecycleListener<Rewrite> listener = this.listeners.get(j);
            if (listener.handles(event))
                listener.afterInboundRewrite((Rewrite) event);
        }
        int handlerCount = this.resultHandlers.size();
        for (int k = 0; k < handlerCount; k++) {
            if (((RewriteResultHandler) this.resultHandlers.get(k)).handles(event))
                ((RewriteResultHandler) this.resultHandlers.get(k)).handleResult((Rewrite) event);
        }
    }

    public void destroy() {
        log.info("RewriteFilter shutting down...");
        for (RewriteProvider<ServletContext, Rewrite> provider : this.providers) {
            if (provider instanceof ServletRewriteProvider)
                ((ServletRewriteProvider) provider).shutdown(this.servletContext);
        }
        log.info("RewriteFilter deactivated.");
    }

    private int getFilterCount(ServletRequest request) {
        return ((Integer) request.getAttribute(FILTER_COUNT_KEY)).intValue();
    }

    private void decrementFilterCount(ServletRequest request) {
        Integer count = (Integer) request.getAttribute(FILTER_COUNT_KEY);

        if (count != null) {
            count = Integer.valueOf(count.intValue() - 1);
        }

        request.setAttribute(FILTER_COUNT_KEY, count);
    }

    private void incrementFilterCount(ServletRequest request) {
        Integer count = (Integer) request.getAttribute(FILTER_COUNT_KEY);

        if (count == null) {
            count = Integer.valueOf(1);
        } else {
            count = Integer.valueOf(count.intValue() + 1);
        }

        request.setAttribute(FILTER_COUNT_KEY, count);
    }

    private SystemFacadeLocal lookupSystemFacadeLocal() {
        try {
            return (SystemFacadeLocal) (new InitialContext())
                    .lookup("java:module/SystemFacade!org.isobit.app.ejb.SystemFacadeLocal");
        } catch (NamingException ne) {
            throw new RuntimeException(ne);
        }
    }

    private boolean isStaticResource(String uri) {
        return uri.contains("javax.faces.resource")
                || uri.endsWith(".jpg")
                || uri.endsWith(".js")
                || uri.endsWith(".ttf")
                || uri.endsWith(".properties")
                || uri.endsWith(".png")
                || uri.endsWith(".gif")
                || uri.endsWith(".css")
                || uri.endsWith(".svg")
                || uri.endsWith(".ico");
    }
}
