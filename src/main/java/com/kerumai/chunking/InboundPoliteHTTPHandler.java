package com.kerumai.chunking;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;

/**
 * Should be added to the pipeline AFTER http codecs.
 *
 * User: Mike Smith
 * Date: 2/12/16
 * Time: 2:49 PM
 */
public class InboundPoliteHTTPHandler extends ChannelInboundHandlerAdapter
{
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
}
