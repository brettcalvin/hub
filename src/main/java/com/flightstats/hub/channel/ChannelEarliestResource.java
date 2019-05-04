package com.flightstats.hub.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.dao.aws.ContentRetriever;
import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.model.DirectionQuery;
import com.flightstats.hub.model.Epoch;
import com.flightstats.hub.model.Location;
import com.flightstats.hub.model.Order;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.SortedSet;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.SEE_OTHER;

@Path("/channel/{channel}/earliest")
public class ChannelEarliestResource {

    private final TagEarliestResource tagEarliestResource;
    private final ChannelService channelService;
    private final ContentRetriever contentRetriever;
    private final ObjectMapper objectMapper;

    @Context
    private UriInfo uriInfo;

    @Inject
    public ChannelEarliestResource(TagEarliestResource tagEarliestResource,
                                   ChannelService channelService,
                                   ContentRetriever contentRetriever,
                                   ObjectMapper objectMapper) {
        this.tagEarliestResource = tagEarliestResource;
        this.channelService = channelService;
        this.contentRetriever = contentRetriever;
        this.objectMapper = objectMapper;
    }

    public static DirectionQuery getDirectionQuery(String channel, int count, boolean stable, String location, String epoch) {
        return DirectionQuery.builder()
                .channelName(channel)
                .next(true)
                .stable(stable)
                .count(count)
                .location(Location.valueOf(location))
                .epoch(Epoch.valueOf(epoch))
                .build();
    }

    @GET
    public Response getEarliest(@PathParam("channel") String channel,
                                @QueryParam("stable") @DefaultValue("true") boolean stable,
                                @QueryParam("trace") @DefaultValue("false") boolean trace,
                                @QueryParam("location") @DefaultValue(Location.DEFAULT) String location,
                                @QueryParam("epoch") @DefaultValue(Epoch.DEFAULT) String epoch,
                                @QueryParam("tag") String tag) {
        if (tag != null) {
            return tagEarliestResource.getEarliest(tag, stable, trace, location, epoch, uriInfo);
        }
        DirectionQuery query = getDirectionQuery(channel, 1, stable, location, epoch);
        Collection<ContentKey> keys = contentRetriever.query(query);
        if (keys.isEmpty()) {
            return Response.status(NOT_FOUND).build();
        } else {
            return Response.status(SEE_OTHER)
                    .location(URI.create(uriInfo.getBaseUri() + "channel/" + channel + "/" + keys.iterator().next().toUrl()))
                    .build();
        }
    }

    @GET
    @Path("/{count}")
    @Produces({MediaType.APPLICATION_JSON, "multipart/*", "application/zip"})
    public Response getEarliestCount(@PathParam("channel") String channel,
                                     @PathParam("count") int count,
                                     @QueryParam("stable") @DefaultValue("true") boolean stable,
                                     @QueryParam("trace") @DefaultValue("false") boolean trace,
                                     @QueryParam("batch") @DefaultValue("false") boolean batch,
                                     @QueryParam("bulk") @DefaultValue("false") boolean bulk,
                                     @QueryParam("location") @DefaultValue(Location.DEFAULT) String location,
                                     @QueryParam("epoch") @DefaultValue(Epoch.DEFAULT) String epoch,
                                     @QueryParam("order") @DefaultValue(Order.DEFAULT) String order,
                                     @QueryParam("tag") String tag,
                                     @HeaderParam("Accept") String accept) {
        if (tag != null) {
            return tagEarliestResource.getEarliestCount(tag, count, stable, bulk, batch, trace, location, epoch, order, accept, uriInfo);
        }
        DirectionQuery query = getDirectionQuery(channel, count, stable, location, epoch);
        SortedSet<ContentKey> keys = contentRetriever.query(query);
        if (keys.isEmpty()) {
            return Response.status(NOT_FOUND).build();
        }
        boolean descending = Order.isDescending(order);
        if (bulk || batch) {
            return BulkBuilder.build(keys, channel, channelService, uriInfo, accept, descending);
        } else {
            return LinkBuilder.directionalResponse(keys, count, query, objectMapper, uriInfo, false, trace, descending);
        }
    }


}
