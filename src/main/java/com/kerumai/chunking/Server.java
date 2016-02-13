package com.kerumai.chunking;


import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Mike Smith
 * Date: 02/12/16
 * Time: 2:29 PM
 */
public class Server
{
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final LoggingHandler nettyLogger = new LoggingHandler("server.nettylog", LogLevel.INFO);

    private static final DynamicIntProperty SERVER_SOCKET_TIMEOUT = new DynamicIntProperty("server.netty.connection.socket.timeout", 45 * 000);
    private static final DynamicIntProperty SERVER_CONN_IDLE_TIMEOUT_SECS = new DynamicIntProperty("server.netty.connection.idle.timeout", 30);
    private static final DynamicBooleanProperty USE_EPOLL = new DynamicBooleanProperty("server.netty.socket.epoll", false);

    /**
     * Our {@link ServerGroup}. Multiple proxy servers can share the same
     * ServerGroup in order to reuse threads and other such resources.
     */
    private ServerGroup serverGroup;

    private int port = 7001;


    public static void main(String[] args)
    {
        new Server().start();
    }

    public void start()
    {
        LOG.info("Starting server at port: " + this.port);
        serverGroup = new ServerGroup("ChunkingServer");
        serverGroup.initializeTransport();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap().group(
                    serverGroup.clientToProxyBossPool,
                    serverGroup.clientToProxyWorkerPool);


            LOG.info("Server listening with TCP transport");
            if (USE_EPOLL.get()) {
                serverBootstrap.channel(EpollServerSocketChannel.class);
            }
            else {
                serverBootstrap.channel(NioServerSocketChannel.class);
            }

            // Socket configuration.
            serverBootstrap
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_TIMEOUT, SERVER_SOCKET_TIMEOUT.get())
                    .option(ChannelOption.SO_KEEPALIVE, true);
            serverBootstrap.childHandler(new ServerChannelInitializer());
            serverBootstrap.validate();

            // Bind and start to accept incoming connections.
            ChannelFuture f = serverBootstrap.bind(port).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Main thread interrupted, so shutting down.");
        }
        finally {
            serverGroup.stop();
        }

    }

    private class ServerChannelInitializer extends ChannelInitializer<Channel>
    {
        @Override
        protected void initChannel(Channel ch) throws Exception
        {
            // Configure our pipeline of ChannelHandlerS.
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("idleStateHandler", new IdleStateHandler(0, 0, SERVER_CONN_IDLE_TIMEOUT_SECS.get()));
            pipeline.addLast("codec", new HttpServerCodec());
            pipeline.addLast("inboundPoliteHttp", new InboundPoliteHTTPHandler());
            pipeline.addLast("outboundPoliteHttp", new OutboundPoliteHTTPHandler());
            pipeline.addLast("logger", nettyLogger);
            pipeline.addLast(new AppHandler());
        }
    }

    private static class ServerGroup
    {
        private DynamicIntProperty INCOMING_ACCEPTOR_THREADS = new DynamicIntProperty("zuul.server.netty.threads.acceptor", 1);
        private DynamicIntProperty INCOMING_WORKER_THREADS = new DynamicIntProperty("zuul.server.netty.threads.worker", 4);

        /** A name for this ServerGroup to use in naming threads. */
        private final String name;

        private EventLoopGroup clientToProxyBossPool;
        private EventLoopGroup clientToProxyWorkerPool;

        private volatile boolean stopped = false;

        private ServerGroup(String name) {
            this.name = name;

            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(final Thread t, final Throwable e) {
                    LOG.error("Uncaught throwable", e);
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(), "Chunking-ServerGroup-JVM-shutdown-hook"));
        }

        private void initializeTransport()
        {
            SelectorProvider selectorProvider = SelectorProvider.provider();

            // TODO - Use EpollEventLoopGroup instead for these.
            NioEventLoopGroup inboundAcceptorGroup = new NioEventLoopGroup(
                    INCOMING_ACCEPTOR_THREADS.get(),
                    new CategorizedThreadFactory("ServerAcceptor"),
                    selectorProvider);
            NioEventLoopGroup inboundWorkerGroup = new NioEventLoopGroup(
                    INCOMING_WORKER_THREADS.get(),
                    new CategorizedThreadFactory("ServerWorker"),
                    selectorProvider);
            inboundWorkerGroup.setIoRatio(90);

            this.clientToProxyBossPool = inboundAcceptorGroup;
            this.clientToProxyWorkerPool = inboundWorkerGroup;
        }

        synchronized private void stop()
        {
            LOG.info("Shutting down");
            if (stopped) {
                LOG.info("Already stopped");
                return;
            }

            LOG.info("Shutting down event loops");
            List<EventLoopGroup> allEventLoopGroups = new ArrayList<>();
            allEventLoopGroups.add(clientToProxyBossPool);
            allEventLoopGroups.add(clientToProxyWorkerPool);
            for (EventLoopGroup group : allEventLoopGroups) {
                group.shutdownGracefully();
            }

            for (EventLoopGroup group : allEventLoopGroups) {
                try {
                    group.awaitTermination(60, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    LOG.warn("Interrupted while shutting down event loop");
                }
            }

            stopped = true;
            LOG.info("Done shutting down");
        }

        private class CategorizedThreadFactory implements ThreadFactory {
            private String category;
            private int num = 0;

            public CategorizedThreadFactory(String category) {
                super();
                this.category = category;
            }

            public Thread newThread(final Runnable r) {
                final FastThreadLocalThread t = new FastThreadLocalThread(r,
                        name + "-" + category + "-" + num++);
                return t;
            }
        }
    }
}
