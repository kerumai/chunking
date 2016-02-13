package com.kerumai.chunking;

import com.netflix.config.DynamicLongProperty;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;

import java.util.concurrent.TimeUnit;

/**
 * User: Mike Smith
 * Date: 2/12/16
 * Time: 2:26 PM
 */
public class AppHandler extends ChannelInboundHandlerAdapter
{
    private static final DynamicLongProperty CHUNK_PAUSE_MS = new DynamicLongProperty("server.chunk.pause", 2000);
    private HttpRequest request = null;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof HttpRequest) {
            request = (HttpRequest) msg;
        }
        else if (msg instanceof LastHttpContent) {
            respond(ctx);
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
        else {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            ctx.write(response);
            ctx.writeAndFlush(new DefaultLastHttpContent());
        }
    }
}
