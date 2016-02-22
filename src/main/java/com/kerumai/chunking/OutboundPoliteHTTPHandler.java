package com.kerumai.chunking;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: Mike Smith
 * Date: 2/12/16
 * Time: 3:05 PM
 */
public class OutboundPoliteHTTPHandler extends ChannelOutboundHandlerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(OutboundPoliteHTTPHandler.class);

    private final static AttributeKey<Boolean> KEY_SHOULD_CLOSE = AttributeKey.newInstance("_hvoh_close_conn");
    private final static AttributeKey<Boolean> KEY_CLOSE_NOW = AttributeKey.newInstance("_hvoh_close_now");


    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
    {
        if (msg instanceof HttpResponse) {
            try {
                HttpResponse response = (HttpResponse) msg;
                HttpRequest request = ctx.attr(InboundPoliteHTTPHandler.KEY_REQUEST).get();

                // Fixup http version of response if needed.
                if (!response.protocolVersion().equals(request.protocolVersion())) {
                    response.setProtocolVersion(request.protocolVersion());
                }

                // Netty does not automatically add Content-Length or Transfer-Encoding: chunked. So we add here if missing.
                if (!HttpUtil.isContentLengthSet(response) && !HttpUtil.isTransferEncodingChunked(response)) {
                    response.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }

                // We MUST set a Connection: keep-alive response header for HTTP/1.0 clients as otherwise
                // some of them fail to receive the payload, and/or can't re-use the connection (found this with ab).
                if (HttpUtil.isKeepAlive(request)) {
                    // Set a Connection: keep-alive header on response.
                    HttpUtil.setKeepAlive(response, true);
                } else {
                    // Send a Connection: close response header (only needed for HTTP/1.0).
                    if (request.protocolVersion().equals(HttpVersion.HTTP_1_0)) {
                        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                    }

                    // TODO - Flag to close the connection.
                    ctx.attr(KEY_SHOULD_CLOSE).set(Boolean.TRUE);
                }
            }
            finally {
                // Cleanup.
                ctx.attr(InboundPoliteHTTPHandler.KEY_REQUEST).remove();
            }
        }


        try {
            // Invoke rest of the pipeline.
            super.write(ctx, msg, promise);

        }
        finally {
            if (msg instanceof LastHttpContent) {
                // Response is finished. So if needed, close the connection.
                Boolean shouldClose = ctx.attr(KEY_SHOULD_CLOSE).get();
                if (shouldClose != null && shouldClose.booleanValue()) {
                    ctx.attr(KEY_CLOSE_NOW).set(Boolean.TRUE);
                }
            }
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception
    {
        try {
            super.flush(ctx);
        }
        finally {
            // Response is finished. So if needed, close the connection.
            Boolean closeNow = ctx.attr(KEY_CLOSE_NOW).get();
            if (closeNow != null && closeNow.booleanValue()) {
                ctx.close();
            }
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception
    {
        // We choose when to close the channel ourselves, so ignore this invocation.
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        HttpRequest request = ctx.attr(InboundPoliteHTTPHandler.KEY_REQUEST).get();

        LOG.error("Unhandled exception caught. uri=" + request.uri(), cause);

        Utils.sendDefaultErrorResponse(ctx);

        super.exceptionCaught(ctx, cause);
    }
}
