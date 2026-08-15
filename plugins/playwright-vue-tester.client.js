/**
 * Playwright Vue Component Tester - Client UI Plugin
 * 
 * Browser-side plugin that provides:
 * 1. Test panel in conversation input area
 * 2. Status overlay
 */

(function() {
  'use strict';
  
  function createPlugin(ctx) {
    const slots = ctx.get('slots');
    if (!slots) return null;
    
    // Inject styles
    ctx.effect(function() {
      const style = styles.insert(`
        .pvct-panel {
          padding: 4px 8px;
          display: flex;
          align-items: center;
          gap: 6px;
          font-size: 12px;
        }
        .pvct-panel select {
          padding: 2px 4px;
          border-radius: 3px;
          border: 1px solid var(--ds-border, #d0d7de);
          background: var(--ds-input-background, transparent);
          color: var(--ds-text, inherit);
        }
        .pvct-panel button {
          padding: 2px 8px;
          border-radius: 3px;
          border: 1px solid var(--ds-border, #d0d7de);
          background: var(--ds-button-primary, #0969da);
          color: var(--ds-text, white);
          cursor: pointer;
          font-size: 11px;
        }
        .pvct-panel button:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
        .pvct-overlay {
          position: fixed;
          bottom: 16px;
          right: 16px;
          background: var(--ds-surface-1, #ffffff);
          border: 1px solid var(--ds-border, #d0d7de);
          border-radius: 8px;
          padding: 10px 14px;
          font-size: 11px;
          z-index: 1000;
          box-shadow: var(--ds-shadow-1, 0 1px 3px rgba(0,0,0,0.12));
          max-width: 280px;
        }
        .pvct-overlay-title {
          font-weight: 600;
          margin-bottom: 4px;
          color: var(--ds-text, inherit);
        }
        .pvct-overlay-subtitle {
          color: var(--ds-text-secondary, #656d76);
        }
      `);
      return function() { style(); };
    }, 'pvct-styles');
    
    // Register test panel in conversation input right area
    slots.inject('conversation.input.right', function() {
      slots.register(
        { name: 'conversation.input.right', id: 'playwright-test-panel' },
        function(props) {
          return React.createElement(TestPanel, { props: props });
        }
      );
    });
    
    // Register status overlay
    slots.inject('shell.overlay', function() {
      slots.register(
        { name: 'shell.overlay', id: 'playwright-test-overlay' },
        function(props) {
          return React.createElement(TestOverlay, { props: props });
        }
      );
    });
    
    return {};
  }
  
  // Test Panel Component
  function TestPanel(props) {
    const [running, setRunning] = React.useState(false);
    const [result, setResult] = React.useState(null);
    const [component, setComponent] = React.useState('CounterWidget');
    
    async function runTest() {
      setRunning(true);
      setResult(null);
      try {
        const res = await host.call('run-test', { 
          component: component, 
          delayMs: 500, 
          headless: false 
        });
        setResult(res);
      } catch (e) {
        setResult({ success: false, error: e.message });
      } finally {
        setRunning(false);
      }
    }
    
    return React.createElement('div', { className: 'pvct-panel' },
      React.createElement('span', { 
        style: { color: 'var(--ds-text-secondary, #656d76)' } 
      }, 'Vue Test:'),
      React.createElement('select', {
        value: component,
        onChange: function(e) { setComponent(e.target.value); }
      },
        React.createElement('option', { value: 'CounterWidget' }, 'CounterWidget'),
        React.createElement('option', { value: 'AsyncLoader' }, 'AsyncLoader')
      ),
      React.createElement('button', {
        onClick: runTest,
        disabled: running
      }, running ? 'Running...' : 'Run Test'),
      result && React.createElement('span', {
        style: {
          color: result.success ? '#22c55e' : '#ef4444',
          marginLeft: '4px'
        }
      }, result.success 
        ? 'OK ' + result.totalElapsedMs + 'ms' 
        : 'ERR')
    );
  }
  
  // Status Overlay Component
  function TestOverlay(props) {
    const [status, setStatus] = React.useState(null);
    
    React.useEffect(function() {
      host.call('get-status').then(function(s) {
        setStatus(s);
      }).catch(function() {});
    }, []);
    
    if (!status) return React.createElement('div', null, '');
    
    return React.createElement('div', { className: 'pvct-overlay' },
      React.createElement('div', { className: 'pvct-overlay-title' }, 
        'Playwright Vue Tester'),
      React.createElement('div', { className: 'pvct-overlay-subtitle' },
        'Delay: ' + status.networkDelayMs + 'ms | Pages: ' + 
        Object.keys(status.testPages || {}).join(', ')
      )
    );
  }
  
  // Export for Cordis
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = createPlugin;
  }
})();
