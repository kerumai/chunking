package com.kerumai.chunking;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

/**
 * User: Mike Smith
 * Date: 2/21/16
 * Time: 2:47 PM
 */
public class Utils
{
    public static void sendDefaultErrorResponse(ChannelHandlerContext ctx)
    {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.write(response);
        ctx.writeAndFlush(new DefaultLastHttpContent());
    }
}
