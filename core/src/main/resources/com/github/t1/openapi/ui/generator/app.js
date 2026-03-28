document.addEventListener('DOMContentLoaded', function() {
    var detail = document.getElementById('detail');

    // Mode toggle consumer
    var modeContainer = document.querySelector('[data-toggle="mode"]');
    if (modeContainer) {
        modeContainer.addEventListener('toggle', function(e) {
            modeContainer.setAttribute('data-mode', e.detail.value);
            var sendBtns = document.querySelectorAll('#detail button[type=submit]');
            sendBtns.forEach(function(b) { b.textContent = e.detail.value === 'try' ? 'Send' : 'Copy'; });
        });
        modeContainer.addEventListener('keydown', function(e) {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                var viewToggle = document.querySelector('[data-toggle="view"]');
                if (viewToggle) viewToggle.focus();
            }
        });
        document.addEventListener('keydown', function(e) {
            if (e.key >= '1' && e.key <= '3') {
                var tag = document.activeElement.tagName;
                if (tag === 'INPUT' || tag === 'TEXTAREA') return;
                if (document.activeElement.isContentEditable) return;
                e.preventDefault();
                var values = Array.from(modeContainer.querySelectorAll('[data-toggle-value]')).map(function(el) {
                    return el.getAttribute('data-toggle-value');
                });
                modeContainer._select(values[parseInt(e.key) - 1]);
            }
        });
    }

    // View toggle consumer
    var viewToggle = document.querySelector('[data-toggle="view"]');
    if (viewToggle) {
        viewToggle.addEventListener('toggle', function(e) {
            var btn = viewToggle.querySelector('[data-toggle-value=' + e.detail.value + ']');
            htmx.ajax('GET', btn.getAttribute('hx-get'), {target: '#tree-container', swap: 'innerHTML'}).then(function() {
                viewToggle.focus();
            });
        });
        viewToggle.addEventListener('keydown', function(e) {
            if (e.key === 'ArrowUp') {
                e.preventDefault();
                var modeToggle = document.querySelector('[data-toggle="mode"]');
                if (modeToggle) modeToggle.focus();
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                var tree = document.querySelector('[role="tree"]');
                if (tree) tree.focus();
            } else if (e.key === 'Tab') {
                e.preventDefault();
                if (e.shiftKey) {
                    var modeToggle = document.querySelector('[data-toggle="mode"]');
                    if (modeToggle) modeToggle.focus();
                } else {
                    var tree = document.querySelector('[role="tree"]');
                    if (tree) tree.focus();
                }
            }
        });
    }

    // Restore persisted toggle state (after consumers registered)
    document.querySelectorAll('.toggle[data-persist]').forEach(function(container) {
        var saved = localStorage.getItem(container.getAttribute('data-persist'));
        if (saved && saved !== container.querySelector('.is-active').getAttribute('data-toggle-value')) {
            container._select(saved);
        }
    });

    // Tab switching — toggle is-active when HTMX swaps method content
    document.body.addEventListener('htmx:afterRequest', function(e) {
        var tabLink = e.detail.elt;
        if (tabLink && tabLink.closest && tabLink.closest('.tabs')) {
            var tabs = tabLink.closest('.tabs');
            tabs.querySelectorAll('li').forEach(function(li) { li.classList.remove('is-active'); });
            tabLink.closest('li').classList.add('is-active');
        }
    });

    function initDescriptionToggle() {
        document.querySelectorAll('.op-description-wrapper').forEach(function(wrapper) {
            var desc = wrapper.querySelector('.op-description');
            var toggle = wrapper.querySelector('.desc-toggle');
            if (!desc || !toggle) return;
            if (desc.scrollHeight > desc.clientHeight) {
                wrapper.classList.add('is-clamped');
            } else {
                wrapper.classList.remove('is-clamped');
            }
            toggle.onclick = function() {
                var expanded = wrapper.classList.toggle('is-expanded');
                wrapper.classList.toggle('is-clamped', !expanded);
                toggle.querySelector('span').textContent = expanded ? '▾' : '▸';
                toggle.setAttribute('aria-label', expanded ? 'Collapse description' : 'Expand description');
            };
        });
    }

    document.body.addEventListener('htmx:afterSettle', function(e) {
        if (e.detail.target && e.detail.target.id === 'tree-container' && viewToggle) {
            viewToggle.focus();
        }
    });
    document.body.addEventListener('htmx:load', function(e) {
        if (!pendingMethod || !detail || !detail.contains(e.target)) return;
        var method = pendingMethod;
        pendingMethod = null;
        var tabLinks = detail.querySelectorAll('.tabs li a');
        tabLinks.forEach(function(a) {
            if (a.textContent.trim() === method) a.click();
        });
    });
    document.body.addEventListener('htmx:afterSwap', function(e) {
        var currentMode = modeContainer ? modeContainer.getAttribute('data-mode') : 'try';
        if (currentMode !== 'try') {
            var sendBtns = document.querySelectorAll('#detail button[type=submit]');
            sendBtns.forEach(function(b) { b.textContent = 'Copy'; });
        }
        initDescriptionToggle();
        document.querySelectorAll('select[data-example-select]').forEach(function(sel) {
            sel.addEventListener('change', function() {
                var textarea = sel.closest('.field').querySelector('textarea[data-request-body]');
                if (textarea) textarea.value = sel.value;
            });
        });
        // After detail content swaps, check if a specific method tab should be activated
        var trigger = e.detail.elt;
    });

    function prettyPrintXml(xml) {
        var doc = new DOMParser().parseFromString(xml, 'application/xml');
        if (doc.querySelector('parsererror')) return xml;
        var xslt = new DOMParser().parseFromString(
            '<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">' +
            '<xsl:output method="xml" indent="yes"/>' +
            '<xsl:template match="@*|node()"><xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy></xsl:template>' +
            '</xsl:stylesheet>', 'application/xml');
        var processor = new XSLTProcessor();
        processor.importStylesheet(xslt);
        var result = processor.transformToDocument(doc);
        return new XMLSerializer().serializeToString(result);
    }

    function detectLanguage(contentType) {
        var mime = (contentType || '').split(';')[0].trim();
        var subtype = mime.split('/')[1] || '';
        var suffix = subtype.includes('+') ? subtype.split('+').pop() : subtype;
        if (typeof hljs !== 'undefined' && hljs.getLanguage(suffix)) return suffix;
        return null;
    }

    // Schema box toggle
    detail.addEventListener('click', function(e) {
        var toggle = e.target.closest('.schema-toggle');
        if (toggle) {
            var box = toggle.closest('.schema-box');
            box.classList.toggle('is-collapsed');
            toggle.textContent = box.classList.contains('is-collapsed') ? 'Schema ▸' : 'Schema ▾';
            return;
        }
        var nestedToggle = e.target.closest('.schema-nested-toggle');
        if (nestedToggle) {
            var expanded = nestedToggle.getAttribute('aria-expanded') === 'true';
            nestedToggle.setAttribute('aria-expanded', expanded ? 'false' : 'true');
            // find the .schema-nested sibling: it's in the same grid, after the details span
            var propName = nestedToggle.closest('.schema-prop-name');
            var row = propName;
            while (row && !(row.nextElementSibling && row.nextElementSibling.classList.contains('schema-nested'))) {
                row = row.nextElementSibling;
            }
            if (row && row.nextElementSibling) row.nextElementSibling.classList.toggle('is-expanded');
            return;
        }
        var tab = e.target.closest('.schema-status-tab');
        if (tab) activateStatusTab(tab);
    });

    function activateStatusTab(tab) {
        var tabs = tab.closest('.schema-status-tabs');
        tabs.querySelectorAll('.schema-status-tab').forEach(function(t) { t.classList.remove('is-active'); });
        tab.classList.add('is-active');
        tab.focus();
        var box = tab.closest('.schema-box');
        box.querySelectorAll('.schema-status-panel').forEach(function(p) { p.style.display = 'none'; });
        var panel = box.querySelector('.schema-status-panel[data-status="' + tab.textContent + '"]');
        if (panel) panel.style.display = '';
    }

    // Status code tab keyboard navigation
    document.addEventListener('keydown', function(e) {
        var focused = document.activeElement;
        if (!focused || !focused.classList.contains('schema-status-tab')) return;
        if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
        e.preventDefault();
        e.stopImmediatePropagation();
        if (e.key === 'ArrowRight') {
            var next = focused.nextElementSibling;
            if (next) activateStatusTab(next);
            else bump(focused, 'h');
        } else {
            var prev = focused.previousElementSibling;
            if (prev) activateStatusTab(prev);
            else bump(focused, 'h');
        }
    }, true);

    function clearPreviousResponse() {
        var existing = detail.querySelector('pre.response');
        if (existing) existing.remove();
        var noBody = detail.querySelector('.response-no-body');
        if (noBody) noBody.remove();
        var headersToggle = detail.querySelector('.response-headers-toggle');
        if (headersToggle) headersToggle.remove();
        var headers = detail.querySelector('.response-headers');
        if (headers) headers.remove();
    }

    function showResponseStatus(btn, status, statusText, headers) {
        var levelRight = btn.closest('.level').querySelector('.level-right');
        levelRight.textContent = '';
        var badge = document.createElement('span');
        badge.className = 'response-status';
        badge.textContent = status + ' ' + statusText;
        if (status >= 200 && status < 300) badge.classList.add('is-success');
        else badge.classList.add('is-error');
        levelRight.appendChild(badge);
        if (headers && headers.length > 0) {
            var toggle = document.createElement('button');
            toggle.type = 'button';
            toggle.className = 'response-headers-toggle';
            toggle.textContent = 'headers (' + headers.length + ') \u25B8';
            toggle.addEventListener('click', function() {
                var container = detail.querySelector('.response-headers');
                if (container) {
                    var visible = container.classList.toggle('is-visible');
                    toggle.textContent = 'headers (' + headers.length + ') ' + (visible ? '\u25BE' : '\u25B8');
                }
            });
            levelRight.appendChild(toggle);
            var container = document.createElement('div');
            container.className = 'response-headers';
            var grid = document.createElement('div');
            grid.className = 'response-header-rows';
            headers.forEach(function(h) {
                var name = document.createElement('span');
                name.className = 'response-header-name';
                name.textContent = h.name;
                var value = document.createElement('span');
                value.className = 'response-header-value';
                value.setAttribute('data-header', h.name);
                value.textContent = h.value;
                grid.appendChild(name);
                grid.appendChild(value);
            });
            container.appendChild(grid);
            var level = btn.closest('.level');
            level.parentNode.insertBefore(container, level.nextSibling);
        }
    }

    function showCopied(btn) {
        var original = btn.textContent;
        btn.textContent = 'Copied!';
        setTimeout(function() { btn.textContent = original; }, 1500);
    }

    // Tab keyboard navigation
    document.addEventListener('keydown', function(e) {
        var focused = document.activeElement;
        if (!focused || !focused.closest || !focused.closest('.tabs')) return;
        if (['ArrowRight','ArrowLeft','ArrowDown','ArrowUp','Enter','Escape'].indexOf(e.key) < 0) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        function activateTab(li) {
            var tabs = li.closest('.tabs');
            tabs.querySelectorAll('li').forEach(function(l) { l.classList.remove('is-active'); });
            li.classList.add('is-active');
            var link = li.querySelector('a');
            link.focus();
            htmx.ajax('GET', link.getAttribute('hx-get'), link.getAttribute('hx-target'));
        }
        if (e.key === 'ArrowRight') {
            var nextLi = focused.closest('li').nextElementSibling;
            if (nextLi) activateTab(nextLi);
            else bump(focused, 'h');
        } else if (e.key === 'ArrowUp') {
            bump(focused, 'v');
        } else if (e.key === 'ArrowLeft') {
            var prevLi = focused.closest('li').previousElementSibling;
            if (prevLi) {
                activateTab(prevLi);
            } else {
                document.querySelector('[role="tree"]').focus();
            }
        } else if (e.key === 'ArrowDown' || e.key === 'Enter') {
            var firstInput = document.querySelector('#method-content input, #method-content select, #method-content textarea, #method-content .schema-toggle, #method-content button[type=submit]');
            if (firstInput) firstInput.focus();
        } else if (e.key === 'Escape') {
            document.querySelector('[role="tree"]').focus();
        }
    }, true);

    // Description toggle arrow key navigation
    document.addEventListener('keydown', function(e) {
        if (!document.activeElement.classList.contains('desc-toggle')) return;
        if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
        e.preventDefault();
        e.stopImmediatePropagation();
        var all = Array.from(document.querySelectorAll('[tabindex], a, button, input, textarea, select'));
        all = all.filter(function(el) { return el.tabIndex >= 0 && el.offsetParent !== null; });
        var idx = all.indexOf(document.activeElement);
        var next = e.key === 'ArrowDown' ? all[idx + 1] : all[idx - 1];
        if (next) next.focus();
    }, true);

    // Field navigation within method content
    document.addEventListener('keydown', function(e) {
        var mc = document.getElementById('method-content') || document.getElementById('detail');
        if (!mc) return;
        var focusables = Array.from(mc.querySelectorAll('input, select, textarea, .schema-toggle, button[type=submit]'));
        var idx = focusables.indexOf(document.activeElement);
        if (idx < 0) return;

        var el = document.activeElement;
        if (el.tagName === 'TEXTAREA' && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
            var val = el.value;
            var pos = el.selectionStart;
            if (e.key === 'ArrowDown') {
                var atLastLine = val.indexOf('\n', pos) < 0;
                if (!atLastLine) return;
            } else {
                var atFirstLine = val.lastIndexOf('\n', pos - 1) < 0;
                if (!atFirstLine) return;
            }
        }
        var handled = true;
        if (e.key === 'ArrowDown') {
            if (idx < focusables.length - 1) focusables[idx + 1].focus();
            else bump(focusables[idx], 'v');
        } else if (e.key === 'ArrowUp') {
            if (idx > 0) {
                focusables[idx - 1].focus();
            } else {
                var activeTabLink = document.querySelector('.tabs .is-active a');
                if (activeTabLink) activeTabLink.focus();
                else {
                    var tree = document.querySelector('[role="tree"]');
                    if (tree) tree.focus();
                }
            }
        } else if ((e.key === 'ArrowRight' || e.key === 'ArrowLeft') && el.closest('.schema-box-header')) {
            var header = el.closest('.schema-box-header');
            var headerFocusables = Array.from(header.querySelectorAll('select, .schema-toggle'));
            var hIdx = headerFocusables.indexOf(el);
            if (e.key === 'ArrowRight' && hIdx < headerFocusables.length - 1) headerFocusables[hIdx + 1].focus();
            else if (e.key === 'ArrowLeft' && hIdx > 0) headerFocusables[hIdx - 1].focus();
            else bump(el, 'h');
        } else if (e.key === 'Enter' && el.classList.contains('schema-toggle')) {
            el.click();
        } else if (e.key === 'Enter' && el.tagName !== 'SELECT') {
            var sendBtn = mc.querySelector('button[type=submit]');
            if (sendBtn) sendBtn.click();
        } else if (e.key === 'Escape') {
            document.querySelector('[role="tree"]').focus();
        } else {
            handled = false;
        }
        if (handled) { e.preventDefault(); e.stopPropagation(); }
    }, true);

    // When clicking a method badge in the tree, navigate to that tab
    var pendingMethod = null;
    document.addEventListener('click', function(e) {
        var badge = e.target.closest('[role="treeitem"] .tag');
        if (!badge) return;
        pendingMethod = badge.textContent.trim();
    }, true);

    // Auto-load the first operation
    var firstHxEl = document.querySelector('#tree-container [hx-get]');
    if (firstHxEl) htmx.ajax('GET', firstHxEl.getAttribute('hx-get'), '#detail');

    // Send button handler (delegated from detail pane)
    if (detail) {
        detail.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' && e.target.closest('form[data-path]') && e.target.tagName !== 'BUTTON') {
                e.preventDefault();
            }
        });
        detail.addEventListener('submit', function(e) {
            e.preventDefault();
            var sendForm = e.target.closest('form[data-path]');
            if (!sendForm) return;
            var sendBtn = sendForm.querySelector('button[type=submit]');

            var pathTemplate = sendForm.getAttribute('data-path');
            var method = sendForm.getAttribute('data-method');
            var modeEl = document.querySelector('[data-toggle="mode"]');
            var mode = modeEl ? modeEl.getAttribute('data-mode') : 'try';
            var baseUrl = modeEl ? (modeEl.getAttribute('data-base-url') || '') : '';

            // Collect input values
            var inputs = sendForm.querySelectorAll('input[name], select[name]');
            var resolvedPath = pathTemplate;
            var queryParams = [];
            inputs.forEach(function(inp) {
                var name = inp.getAttribute('name');
                var isCheckbox = inp.type === 'checkbox';
                var val = isCheckbox ? (inp.checked ? 'true' : '') : inp.value;
                if (pathTemplate.includes('{' + name + '}')) {
                    resolvedPath = resolvedPath.replace('{' + name + '}', encodeURIComponent(val));
                } else if (val) {
                    queryParams.push(name + '=' + encodeURIComponent(val));
                }
            });
            var url = baseUrl.startsWith('http') ? baseUrl + resolvedPath
                    : new URL((baseUrl + resolvedPath).replace(/\/+/g, '/'), window.location.origin).href;
            if (queryParams.length > 0) url += '?' + queryParams.join('&');

            var bodyTextarea = sendForm.querySelector('textarea[data-request-body]');
            var bodyValue = bodyTextarea ? bodyTextarea.value : '';

            if (mode === 'curl') {
                var cmd = bodyValue
                        ? 'curl -X ' + method + " -H 'Content-Type: application/json' -d '" + bodyValue + "' " + url
                        : 'curl -X ' + method + ' ' + url;
                navigator.clipboard.writeText(cmd);
                showCopied(sendBtn);
            } else if (mode === 'httpie') {
                var cmd = bodyValue
                        ? "echo '" + bodyValue + "' | http " + method + ' ' + url + " Content-Type:application/json"
                        : 'http ' + method + ' ' + url;
                navigator.clipboard.writeText(cmd);
                showCopied(sendBtn);
            } else if (mode === 'try') {
                sendBtn.disabled = true;
                sendBtn.textContent = 'Sending...';
                var fetchOptions = { method: method, headers: {} };
                if (bodyValue) {
                    fetchOptions.body = bodyValue;
                    fetchOptions.headers['Content-Type'] = 'application/json';
                }
                var acceptSelect = detail.querySelector('[data-accept] select');
                if (acceptSelect && acceptSelect.value) {
                    fetchOptions.headers['Accept'] = acceptSelect.value;
                }
                fetch(url, fetchOptions).then(function(resp) {
                    var ct = resp.headers.get('Content-Type') || '';
                    var headers = [];
                    resp.headers.forEach(function(value, name) {
                        headers.push({name: name, value: value});
                    });
                    return resp.text().then(function(text) {
                        clearPreviousResponse();
                        showResponseStatus(sendBtn, resp.status, resp.statusText, headers);
                        if (ct.includes('json')) {
                            try { text = JSON.stringify(JSON.parse(text), null, 2); } catch(e) {}
                        } else if (ct.includes('xml')) {
                            try { text = prettyPrintXml(text); } catch(e) {}
                        }
                        if (text.trim()) {
                            var pre = document.createElement('pre');
                            pre.className = 'response';
                            var lang = detectLanguage(ct);
                            if (lang && typeof hljs !== 'undefined') {
                                var code = document.createElement('code');
                                code.className = 'language-' + lang;
                                code.textContent = text;
                                pre.appendChild(code);
                                hljs.highlightElement(code);
                            } else {
                                pre.textContent = text;
                            }
                            detail.appendChild(pre);
                        } else {
                            var msg = document.createElement('p');
                            msg.textContent = 'no body';
                            msg.className = 'response-no-body';
                            detail.appendChild(msg);
                        }
                    });
                }).catch(function(err) {
                    clearPreviousResponse();
                    showResponseStatus(sendBtn, 0, 'Network error');
                    var pre = document.createElement('pre');
                    pre.textContent = err.message;
                    pre.className = 'response';
                    detail.appendChild(pre);
                }).finally(function() {
                    sendBtn.disabled = false;
                    sendBtn.textContent = 'Send';
                    sendBtn.focus();
                });
            }
        });
    }
    // htmx error retry with banner
    var banner = document.getElementById('error-banner');
    var activeRetries = 0;
    function showBanner() {
        activeRetries++;
        banner.style.display = '';
    }
    function hideBannerIfDone() {
        activeRetries--;
        if (activeRetries <= 0) {
            activeRetries = 0;
            banner.style.display = 'none';
        }
    }
    function retryHtmx(url, targetEl) {
        showBanner();
        var interval = setInterval(function() {
            fetch(url).then(function(resp) {
                if (!resp.ok) return;
                clearInterval(interval);
                return resp.text().then(function(html) {
                    if (targetEl) targetEl.innerHTML = html;
                    hideBannerIfDone();
                    if (typeof htmx !== 'undefined') htmx.process(targetEl);
                });
            }).catch(function() {});
        }, 1000);
    }
    function htmxErrorUrl(e) {
        return (e.detail.pathInfo && e.detail.pathInfo.requestPath)
            || (e.detail.elt && e.detail.elt.getAttribute('hx-get'));
    }
    document.body.addEventListener('htmx:sendError', function(e) {
        var url = htmxErrorUrl(e);
        if (!url) return;
        retryHtmx(url, e.detail.target || document.getElementById('detail'));
    });
    document.body.addEventListener('htmx:responseError', function(e) {
        var url = htmxErrorUrl(e);
        if (!url) return;
        retryHtmx(url, e.detail.target || document.getElementById('detail'));
    });
});
