package com.kerumai.chunking;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Should be added to the pipeline AFTER http codecs.
 *
 * User: Mike Smith
 * Date: 2/12/16
 * Time: 2:49 PM
 */
public class InboundPoliteHTTPHandler extends ChannelInboundHandlerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(InboundPoliteHTTPHandler.class);

    public final static AttributeKey<HttpRequest> KEY_REQUEST = AttributeKey.newInstance("_hvih_request");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            ctx.attr(KEY_REQUEST).set(request);



        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        HttpRequest request = ctx.attr(KEY_REQUEST).get();
        if (request == null) {
            LOG.error("Unhandled exception caught. uri not known.", cause);
        }
        else {
            LOG.error("Unhandled exception caught. uri=" + request.uri(), cause);
        }

        Utils.sendDefaultErrorResponse(ctx);

        super.exceptionCaught(ctx, cause);
    }
}
