package com.kerumai.chunking;

import com.netflix.config.DynamicLongProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * User: Mike Smith
 * Date: 2/12/16
 * Time: 2:26 PM
 */
public class AppHandler extends ChannelInboundHandlerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(AppHandler.class);
    private static final DynamicLongProperty CHUNK_PAUSE_MS = new DynamicLongProperty("server.chunk.pause", 750);
    private HttpRequest request = null;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof HttpRequest) {
            request = (HttpRequest) msg;
        }
        else if (msg instanceof LastHttpContent) {
            try {
                respond(ctx);
            }
            catch (Exception e) {
                LOG.error("Error during respond. uri=" + request.uri(), e);
                Utils.sendDefaultErrorResponse(ctx);
            }
        }
        else {
            // We don't care.
        }
    }

    public void respond(ChannelHandlerContext ctx) throws Exception
    {
        if (request.uri().equals("/")) {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            ctx.write(response);

            String firstChunk = "<html><head><title>Chunking Test</title></head><body><p>First Chunk</p>";

            DefaultHttpContent content = new DefaultHttpContent(Unpooled.wrappedBuffer(firstChunk.getBytes()));
            ctx.writeAndFlush(content);

            ctx.executor().schedule(() -> {

                String secondChunk = "<p>2nd Chunk</p></body></html>";
                DefaultHttpContent content2 = new DefaultHttpContent(Unpooled.wrappedBuffer(secondChunk.getBytes()));
                ctx.writeAndFlush(content2);

                ctx.writeAndFlush(new DefaultLastHttpContent());

                request = null;

            }, CHUNK_PAUSE_MS.get(), TimeUnit.MILLISECONDS);
        }

        else if (request.uri().equals("/ig-eliminating-roundtrips/")) {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            ctx.write(response);

            ByteBuf firstChunk = readFileAsByteBuf("/web/Eliminating-Roundtrips-with-Preconnect_igvita.com_frag1.html");

            DefaultHttpContent content = new DefaultHttpContent(firstChunk);
            ctx.writeAndFlush(content);

            ByteBuf secondChunk = readFileAsByteBuf("/web/Eliminating-Roundtrips-with-Preconnect_igvita.com_frag2.html");
            delayFinalChunk(ctx, secondChunk);
        }

        else if (request.uri().equals("/clean/") || request.uri().startsWith("/clean/?")) {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");

            ByteBuf firstChunk = readFileAsByteBuf("/web/clean/index_frag1.html");
            ByteBuf secondChunk = readFileAsByteBuf("/web/clean/index_frag2.html");

            if (request.uri().contains("chunked=false")) {
                ByteBuf wholePage = Unpooled.wrappedBuffer(firstChunk, secondChunk);

                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, wholePage.capacity());
                ctx.write(response);

                delayFinalChunk(ctx, wholePage);
            }
            else {
                response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                ctx.write(response);

                DefaultHttpContent content = new DefaultHttpContent(firstChunk);
                ctx.writeAndFlush(content);
                delayFinalChunk(ctx, secondChunk);
            }
        }

        else if (request.uri().startsWith("/ig-eliminating-roundtrips/Eliminating-Roundtrips-with-Preconnect_igvita.com_files/")) {
            int startIndex = "/ig-eliminating-roundtrips".length();
            String path = "/web" + request.uri().substring(startIndex);
            serveFile(ctx, path);
        }

        else if (request.uri().startsWith("/clean/")) {
            String path = "/web" + request.uri();
            serveFile(ctx, path);
        }

        else if (request.uri().equals("/favicon.ico")) {
            String path = "/web/clean/favicon.ico";
            serveFile(ctx, path);
        }

        else {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            ctx.write(response);
            ctx.writeAndFlush(new DefaultLastHttpContent());
        }
    }

    private void delayFinalChunk(ChannelHandlerContext ctx, ByteBuf chunk)
    {
        ctx.executor().schedule(() -> {

            DefaultHttpContent content2 = new DefaultHttpContent(chunk);
            ctx.write(content2);
            ctx.writeAndFlush(new DefaultLastHttpContent());

            request = null;

        }, CHUNK_PAUSE_MS.get(), TimeUnit.MILLISECONDS);
    }

    private void serveFile(ChannelHandlerContext ctx, String path)
    {
        ByteBuf bb = readFileAsByteBuf(path);

        String contentType = guessContentType(path);

        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, bb);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bb.array().length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=60, public");

        ctx.write(response);
        ctx.writeAndFlush(new DefaultLastHttpContent());
    }

    private String guessContentType(String path)
    {
        if (path.endsWith(".js")) {
            return "text/javascript";
        }
        if (path.endsWith(".css")) {
            return "text/css";
        }
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (path.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "text/html";
    }

    private ByteBuf readFileAsByteBuf(String path)
    {
        try {
            InputStream input = this.getClass().getResourceAsStream(path);
            return Unpooled.wrappedBuffer(IOUtils.toByteArray(input));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
