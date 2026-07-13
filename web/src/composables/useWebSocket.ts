export function useWebSocket() {
  let ws: WebSocket | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  function connect(url: string, onMessage: (ev: MessageEvent) => void, onOpen?: () => void, onClose?: () => void, onError?: (ev: Event) => void) {
	if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
	ws = new WebSocket(url)
	ws.onopen = () => {
	  onOpen && onOpen()
	  startHeartbeat()
	}
	ws.onmessage = (ev) => {
	  onMessage(ev)
	}
	ws.onclose = () => {
	  stopHeartbeat()
	  scheduleReconnect(() => connect(url, onMessage, onOpen, onClose, onError))
	  onClose && onClose()
	}
	ws.onerror = (ev) => {
	  onError && onError(ev)
	}
  }

  function disconnect() {
	stopHeartbeat()
	if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
	if (ws) { ws.close(); ws = null }
  }

  function startHeartbeat() {
	stopHeartbeat()
	heartbeatTimer = setInterval(() => {
	  if (ws && ws.readyState === WebSocket.OPEN) {
		try { ws.send(JSON.stringify({ type: 'ping' })) } catch {}
	  }
	}, 30000)
  }

  function stopHeartbeat() {
	if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
  }

  function scheduleReconnect(cb: () => void) {
	if (reconnectTimer) return
	reconnectTimer = setTimeout(() => { reconnectTimer = null; cb() }, 3000)
  }

  return { connect, disconnect }
}


