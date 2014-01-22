package com.flightstats.datahub.service.eventing;

import com.flightstats.datahub.dao.ChannelService;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

public class JettyWebSocketServletTest {

	private String channelName;
	private String requestUriString;
	private URI requestUri;
	private WebSocketCreator wsCreator;
	private ChannelNameExtractor channelNameExtractor;
	private HttpServletRequest httpRequest;
	private HttpServletResponse httpResponse;
	private ChannelService channelService;
	private WebSocketServletFactory factory;

	@Before
	public void setup() {
		channelName = "spoon";
		requestUriString = "/channel/spoon/ws";
		requestUri = URI.create(requestUriString);
		wsCreator = mock(WebSocketCreator.class);
		channelNameExtractor = mock(ChannelNameExtractor.class);
		httpRequest = mock(HttpServletRequest.class);
		httpResponse = mock(HttpServletResponse.class);
		channelService = mock(ChannelService.class);
		factory = mock(WebSocketServletFactory.class);
	}

	@Test
	public void testConfigure() throws Exception {
		// This test is admittedly a tad goofy, but just checking in on the jetty APIs
		JettyWebSocketServlet testClass = new JettyWebSocketServlet(wsCreator, null, null);

		testClass.configure(factory);

		verify(factory).setCreator(isA(WebSocketCreator.class));
	}

	@Test
	public void testService() throws Exception {
		//GIVEN
		final AtomicBoolean superWasInvoked = new AtomicBoolean(false);

		when(channelNameExtractor.extractFromWS(requestUri)).thenReturn(channelName);
		when(httpRequest.getRequestURI()).thenReturn(requestUriString);
		when(channelService.channelExists(channelName)).thenReturn(true);

		JettyWebSocketServlet testClass = new JettyWebSocketServlet(wsCreator, channelNameExtractor, channelService) {
			@Override
			protected void invokeSuper(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
				superWasInvoked.set(true);
			}
		};

		//WHEN
		testClass.configure(factory);
		testClass.service(httpRequest, httpResponse);

		//THEN
		assertTrue(superWasInvoked.get());
	}

	@Test
	public void test404() throws Exception {
		//GIVEN
		final AtomicBoolean superWasInvoked = new AtomicBoolean(false);

		when(channelNameExtractor.extractFromWS(requestUri)).thenReturn(channelName);
		when(httpRequest.getRequestURI()).thenReturn(requestUriString);
		when(channelService.channelExists(channelName)).thenReturn(false);        //NOPE!

		JettyWebSocketServlet testClass = new JettyWebSocketServlet(wsCreator, channelNameExtractor, channelService) {
			@Override
			protected void invokeSuper(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
				superWasInvoked.set(true);
			}
		};

		//WHEN
		testClass.configure(factory);
		testClass.service(httpRequest, httpResponse);

		//THEN
		verify(httpResponse).setStatus(HttpServletResponse.SC_NOT_FOUND);
		assertFalse(superWasInvoked.get());
	}

}
