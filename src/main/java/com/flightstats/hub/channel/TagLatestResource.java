package com.flightstats.hub.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightstats.hub.dao.TagService;
import com.flightstats.hub.model.ChannelContentKey;
import com.flightstats.hub.model.DirectionQuery;
import com.flightstats.hub.model.Epoch;
import com.flightstats.hub.model.Location;
import com.flightstats.hub.model.Order;
import com.google.common.base.Optional;

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
import java.util.SortedSet;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.SEE_OTHER;

@SuppressWarnings("WeakerAccess")
@Path("/tag/{tag}/latest")
public class TagLatestResource {

    private ObjectMapper mapper;
    private TagService tagService;

    @Inject
    TagLatestResource(TagService tagService, ObjectMapper mapper) {
        this.tagService = tagService;
        this.mapper = mapper;
    }

    @GET
    public Response getLatest(@PathParam("tag") String tag,
                              @QueryParam("stable") @DefaultValue("true") boolean stable,
                              @QueryParam("trace") @DefaultValue("false") boolean trace,
                              @QueryParam("location") @DefaultValue(Location.DEFAULT) String location,
                              @QueryParam("epoch") @DefaultValue(Epoch.DEFAULT) String epoch,
                              @Context UriInfo uriInfo) {
        Optional<ChannelContentKey> latest = tagService.getLatest(getQuery(tag, stable, location, epoch));
        if (latest.isPresent()) {
            URI uri = uriInfo.getBaseUriBuilder()
                    .path(latest.get().toUrl())
                    .queryParam("tag", tag)
                    .build();
            return Response.status(SEE_OTHER).location(uri).build();
        }
        return Response.status(NOT_FOUND).build();
    }

    private DirectionQuery getQuery(String tag, boolean stable, String location, String epoch) {
        return DirectionQuery.builder()
                .tagName(tag)
                .next(false)
                .stable(stable)
                .count(1)
                .location(Location.valueOf(location))
                .epoch(Epoch.valueOf(epoch))
                .build();
    }

    @GET
    @Path("/{count}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLatestCount(@PathParam("tag") String tag,
                                   @PathParam("count") int count,
                                   @QueryParam("stable") @DefaultValue("true") boolean stable,
                                   @QueryParam("batch") @DefaultValue("false") boolean batch,
                                   @QueryParam("bulk") @DefaultValue("false") boolean bulk,
                                   @QueryParam("trace") @DefaultValue("false") boolean trace,
                                   @QueryParam("location") @DefaultValue(Location.DEFAULT) String location,
                                   @QueryParam("epoch") @DefaultValue(Epoch.DEFAULT) String epoch,
                                   @QueryParam("order") @DefaultValue(Order.DEFAULT) String order,
                                   @HeaderParam("Accept") String accept,
                                   @Context UriInfo uriInfo) {
        Optional<ChannelContentKey> latest = tagService.getLatest(getQuery(tag, stable, location, epoch));
        if (!latest.isPresent()) {
            return Response.status(NOT_FOUND).build();
        }
        DirectionQuery query = DirectionQuery.builder()
                .tagName(tag)
                .startKey(latest.get().getContentKey())
                .next(false)
                .stable(stable)
                .location(Location.valueOf(location))
                .epoch(Epoch.valueOf(epoch))
                .count(count - 1)
                .build();
        SortedSet<ChannelContentKey> keys = tagService.getKeys(query);
        keys.add(latest.get());
        if (bulk || batch) {
            return BulkBuilder.buildTag(tag, keys, tagService.getChannelService(), uriInfo, accept);
        }
        return LinkBuilder.directionalTagResponse(tag, keys, count, query, mapper, uriInfo, true, trace, Order.isDescending(order));
    }

}
