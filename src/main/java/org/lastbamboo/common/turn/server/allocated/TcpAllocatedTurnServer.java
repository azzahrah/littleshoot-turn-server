package org.lastbamboo.common.turn.server.allocated;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilter;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoService;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.IoServiceListener;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.lastbamboo.common.turn.server.TurnClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a TCP allocated TURN server.
 */
public class TcpAllocatedTurnServer implements AllocatedTurnServer, 
    IoServiceListener
    {

    private final Logger LOG = 
        LoggerFactory.getLogger(TcpAllocatedTurnServer.class);

    private final int m_port;

    private final TurnClient m_turnClient;

    private final SocketAcceptor m_acceptor;
    
    /**
     * Creates a new TURN server allocated on behalf of a TURN client.  This
     * server will accept connections with permission to connect to the TURN
     * client and will relay data on the TURN client's behalf.
     * 
     * @param turnClient The TURN client.
     * @param port The port to listen on.
     */
    public TcpAllocatedTurnServer(final TurnClient turnClient, final int port)
        {
        m_turnClient = turnClient;
        m_port = port;
        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
        final Executor executor = Executors.newCachedThreadPool();
        this.m_acceptor = new SocketAcceptor(
            Runtime.getRuntime().availableProcessors() + 1, executor);
        m_acceptor.addListener(this);
        }

    public void start()
        {
        // Note there's no encoder here because we just write raw bytes to
        // remote hosts.  The TURN server is responsible for unwrapping the
        // raw bytes from the TURN Send Indication messages.
        
        // We're receiving raw data on these sockets and packaging it for 
        // our TURN client.  This filter just reads the raw data and
        // encapsulates it in TURN Data Indication messages.
        final IoFilter rawDataFilter = new TurnRawDataFilter();
        m_acceptor.getFilterChain().addLast("to-stun", rawDataFilter);
        
        // The IO handler just processes the Data Indication messages.
        final IoHandler handler = 
            new AllocatedTurnServerIoHandler(this.m_turnClient);
        
        final InetSocketAddress address = new InetSocketAddress(this.m_port);
        try
            {
            m_acceptor.bind(address, handler);
            LOG.debug("Started TCP allocated TURN server!!");
            }
        catch (final IOException e)
            {
            LOG.error("Could not bind server", e);
            }
        }

    public void stop()
        {
        this.m_acceptor.unbindAll();
        }
    
    public void serviceActivated(final IoService service, 
        final SocketAddress serviceAddress, final IoHandler handler, 
        final IoServiceConfig config)
        {
        // TODO Auto-generated method stub
        
        }

    public void serviceDeactivated(final IoService service, 
        final SocketAddress serviceAddress, final IoHandler handler, 
        final IoServiceConfig config)
        {
        // TODO Auto-generated method stub
        
        }

    public void sessionCreated(final IoSession session)
        {
        if (this.m_turnClient.hasIncomingPermission(session))
            {
            this.m_turnClient.addConnection(session);
            }
        else
            {
            session.close();
            }
        }

    public void sessionDestroyed(final IoSession session)
        {
        LOG.debug("Lost connection to: {}", session);
        this.m_turnClient.removeConnection(session);
        }
    }