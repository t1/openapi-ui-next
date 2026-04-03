document.addEventListener('DOMContentLoaded', function() {
    var detail = document.getElementById('detail');
    var fieldCache = new Map();
    var responseCache = new Map();
    var schemaToggleCache = new Map();

    function opKey(form) {
        return form.getAttribute('data-method') + ':' + form.getAttribute('data-path');
    }

    function saveFields(form) {
        var key = opKey(form);
        var fields = {};
        form.querySelectorAll('input[name], select[name], textarea[data-request-body]').forEach(function(el) {
            var name = el.getAttribute('name') || '__body__';
            fields[name] = el.type === 'checkbox' ? el.checked : el.value;
        });
        fieldCache.set(key, fields);
    }

    function restoreFields(form) {
        var key = opKey(form);
        var fields = fieldCache.get(key);
        if (!fields) return;
        form.querySelectorAll('input[name], select[name], textarea[data-request-body]').forEach(function(el) {
            var name = el.getAttribute('name') || '__body__';
            if (!(name in fields)) return;
            if (el.type === 'checkbox') el.checked = fields[name];
            else el.value = fields[name];
        });
    }

    function saveSchemaToggles(form) {
        var key = opKey(form);
        var state = {};
        form.querySelectorAll('.schema-box[data-box]').forEach(function(box) {
            state[box.getAttribute('data-box')] = !box.classList.contains('is-collapsed');
        });
        schemaToggleCache.set(key, state);
    }

    function restoreSchemaToggles(form) {
        var key = opKey(form);
        var state = schemaToggleCache.get(key);
        if (!state) return;
        form.querySelectorAll('.schema-box[data-box]').forEach(function(box) {
            var boxType = box.getAttribute('data-box');
            if (boxType in state) {
                var expanded = state[boxType];
                box.classList.toggle('is-collapsed', !expanded);
                var toggle = box.querySelector('.schema-toggle');
                if (toggle) toggle.textContent = expanded ? 'Schema ▾' : 'Schema ▸';
            }
        });
    }

    function restoreResponse(form) {
        var key = opKey(form);
        var resp = responseCache.get(key);
        if (!resp) return;
        var area = form.querySelector('.response-area');
        if (!area) return;
        showResponse(area, form, resp.status, resp.statusText, resp.headers, resp.body, resp.ct, resp.headersExpanded);
    }

    var shortcutMod = /Mac/.test(navigator.platform) ? 'Ctrl' : 'Alt';

    // Mode toggle consumer
    var modeContainer = document.querySelector('[data-toggle="mode"]');
    if (modeContainer) {
        // Set platform-appropriate keyboard shortcut tooltips
        var modeButtons = modeContainer.querySelectorAll('[data-toggle-value]');
        modeButtons.forEach(function(btn, i) {
            var title = btn.getAttribute('title') || '';
            btn.setAttribute('title', title + ' (' + shortcutMod + '+' + (i + 1) + ')');
        });
        modeContainer.addEventListener('toggle', function(e) {
            modeContainer.setAttribute('data-mode', e.detail.value);
            var isTry = e.detail.value === 'try';
            var sendBtns = document.querySelectorAll('#detail button[type=submit]');
            sendBtns.forEach(function(b) { b.textContent = isTry ? 'Send' : 'Copy'; });
            detail.querySelectorAll('[data-param-in="cookie"]').forEach(function(inp) { inp.disabled = isTry; });
        });
        modeContainer.addEventListener('keydown', function(e) {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                var viewToggle = document.querySelector('[data-toggle="view"]');
                if (viewToggle) viewToggle.focus();
            }
        });
        document.addEventListener('keydown', function(e) {
            var mod = /Mac/.test(navigator.platform) ? e.ctrlKey : e.altKey;
            var digit = mod && e.code >= 'Digit1' && e.code <= 'Digit3' ? parseInt(e.code.charAt(5)) : 0;
            if (digit) {
                e.preventDefault();
                var values = Array.from(modeContainer.querySelectorAll('[data-toggle-value]')).map(function(el) {
                    return el.getAttribute('data-toggle-value');
                });
                modeContainer._select(values[digit - 1]);
            }
        });
    }

    // Global headers panel toggle
    var globalHeadersPanel = document.getElementById('global-headers');
    if (globalHeadersPanel) {
        globalHeadersPanel.querySelector('.global-headers-toggle').addEventListener('click', function() {
            globalHeadersPanel.classList.toggle('is-collapsed');
        });
        globalHeadersPanel.addEventListener('click', function(e) {
            var addBtn = e.target.closest('.custom-header-add');
            if (addBtn) {
                var body = globalHeadersPanel.querySelector('.global-headers-body');
                var row = document.createElement('div');
                row.className = 'custom-header-row';
                row.innerHTML =
                    '<input type="text" class="input is-small custom-header-name" name="' + randomName() + '" placeholder="Header name">' +
                    '<div class="control has-icons-right">' +
                    '<input type="text" class="input is-small custom-header-value" name="' + randomName() + '" placeholder="Value">' +
                    '<span class="icon is-small is-right persist-toggle" aria-pressed="false" title="Pin value (' + shortcutMod + '+P)"><i class="fa-solid fa-thumbtack"></i></span>' +
                    '</div>' +
                    '<button type="button" class="delete is-small custom-header-remove"></button>';
                body.insertBefore(row, addBtn);
                row.querySelector('.custom-header-name').focus();
                updateGlobalHeaderCount();
                applyGlobalHeaderPlaceholders();
                return;
            }
            var removeBtn = e.target.closest('.custom-header-remove');
            if (removeBtn) {
                var row = removeBtn.closest('.custom-header-row');
                var persistBtn = row.querySelector('.persist-toggle');
                if (persistBtn && persistBtn.getAttribute('aria-pressed') === 'true') {
                    var name = row.querySelector('.custom-header-name').value.trim();
                    if (name) localStorage.removeItem('openapi-ui-global-header:' + name);
                }
                row.remove();
                updateGlobalHeaderCount();
                applyGlobalHeaderPlaceholders();
                return;
            }
        });
    }

    function updateGlobalHeaderCount() {
        if (!globalHeadersPanel) return;
        var count = globalHeadersPanel.querySelectorAll('.custom-header-row').length;
        globalHeadersPanel.querySelector('.global-headers-count').textContent = count;
    }

    // Global header persistence
    if (globalHeadersPanel) {
        globalHeadersPanel.addEventListener('click', function(e) {
            var persistBtn = e.target.closest('.persist-toggle');
            if (!persistBtn) return;
            var pressed = persistBtn.getAttribute('aria-pressed') === 'true';
            persistBtn.setAttribute('aria-pressed', String(!pressed));
            var row = persistBtn.closest('.custom-header-row');
            var name = row.querySelector('.custom-header-name').value.trim();
            var value = row.querySelector('.custom-header-value').value;
            if (!pressed && name) {
                localStorage.setItem('openapi-ui-global-header:' + name, value);
                row.setAttribute('data-prev-name', name);
            } else if (name) {
                localStorage.removeItem('openapi-ui-global-header:' + name);
                row.removeAttribute('data-prev-name');
            }
        });
        globalHeadersPanel.addEventListener('input', function(e) {
            var inp = e.target.closest('.custom-header-name, .custom-header-value');
            if (!inp) return;
            applyGlobalHeaderPlaceholders();
            var row = inp.closest('.custom-header-row');
            var persistBtn = row.querySelector('.persist-toggle');
            if (!persistBtn || persistBtn.getAttribute('aria-pressed') !== 'true') return;
            var name = row.querySelector('.custom-header-name').value.trim();
            var value = row.querySelector('.custom-header-value').value;
            var prevName = row.getAttribute('data-prev-name');
            if (prevName && prevName !== name) localStorage.removeItem('openapi-ui-global-header:' + prevName);
            if (name) {
                localStorage.setItem('openapi-ui-global-header:' + name, value);
                row.setAttribute('data-prev-name', name);
            }
        });
        // Restore persisted global headers
        for (var i = 0; i < localStorage.length; i++) {
            var key = localStorage.key(i);
            if (!key.startsWith('openapi-ui-global-header:')) continue;
            var ghName = key.substring('openapi-ui-global-header:'.length);
            var ghValue = localStorage.getItem(key);
            var body = globalHeadersPanel.querySelector('.global-headers-body');
            var addBtn = body.querySelector('.custom-header-add');
            var row = document.createElement('div');
            row.className = 'custom-header-row';
            row.setAttribute('data-prev-name', ghName);
            row.innerHTML =
                '<input type="text" class="input is-small custom-header-name" placeholder="Header name" value="' + ghName.replace(/"/g, '&quot;') + '">' +
                '<div class="control has-icons-right">' +
                '<input type="text" class="input is-small custom-header-value" placeholder="Value" value="' + ghValue.replace(/"/g, '&quot;') + '">' +
                '<span class="icon is-small is-right persist-toggle" aria-pressed="true" title="Pin value (' + shortcutMod + '+P)"><i class="fa-solid fa-thumbtack"></i></span>' +
                '</div>' +
                '<button type="button" class="delete is-small custom-header-remove"></button>';
            body.insertBefore(row, addBtn);
        }
        updateGlobalHeaderCount();
        applyGlobalHeaderPlaceholders();
    }

    function applyGlobalHeaderPlaceholders() {
        var globals = {};
        if (globalHeadersPanel) {
            globalHeadersPanel.querySelectorAll('.custom-header-row').forEach(function(row) {
                var name = row.querySelector('.custom-header-name').value.trim();
                var value = row.querySelector('.custom-header-value').value;
                if (name) globals[name.toLowerCase()] = value;
            });
        }
        document.querySelectorAll('[data-param-in="header"]').forEach(function(el) {
            var name = el.getAttribute('name');
            if (!name) return;
            if (!el.hasAttribute('data-original-placeholder')) {
                el.setAttribute('data-original-placeholder', el.getAttribute('placeholder') || '');
            }
            var globalValue = globals[name.toLowerCase()];
            if (globalValue) {
                el.setAttribute('placeholder', globalValue + ' \u00A0\u00A0\u00A0// from global headers');
            } else {
                var original = el.getAttribute('data-original-placeholder');
                if (original) el.setAttribute('placeholder', original);
                else el.removeAttribute('placeholder');
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

    // Override persisted view when URL hash specifies a view
    if (decodeURIComponent(location.hash).match(/^#\[/)) localStorage.setItem('openapi-ui-view', 'tags');

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
            var hxGet = tabLink.getAttribute('hx-get');
            if (hxGet) history.replaceState(null, '', '#' + hxGetToRoute(hxGet));
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
        if (e.detail.target && e.detail.target.id === 'tree-container') {
            if (window._hashNavPending) {
                window._hashNavPending = false;
                navigateFromHash();
            } else if (viewToggle) {
                viewToggle.focus();
            }
        }
    });
    document.body.addEventListener('htmx:load', function(e) {
        if (!pendingMethod || !detail || !detail.contains(e.target)) return;
        var method = pendingMethod;
        pendingMethod = null;
        var tabLinks = detail.querySelectorAll('.tabs li a');
        tabLinks.forEach(function(a) {
            if (a.textContent.trim() === method) { a.click(); a.focus(); }
        });
    });
    document.body.addEventListener('htmx:afterSwap', function(e) {
        var currentMode = modeContainer ? modeContainer.getAttribute('data-mode') : 'try';
        var isTry = currentMode === 'try';
        if (!isTry) {
            var sendBtns = document.querySelectorAll('#detail button[type=submit]');
            sendBtns.forEach(function(b) { b.textContent = 'Copy'; });
        }
        detail.querySelectorAll('[data-param-in="cookie"]').forEach(function(inp) { inp.disabled = isTry; });
        initDescriptionToggle();
        // Initialize persist toggle icons on server-rendered param fields
        detail.querySelectorAll('.field .icon.is-right').forEach(function(icon) {
            icon.classList.add('persist-toggle');
            icon.setAttribute('aria-pressed', 'false');
            icon.setAttribute('title', 'Pin value (' + shortcutMod + '+P)');
        });
        // Restore persisted spec-defined parameter values
        document.querySelectorAll('[data-param-in]').forEach(function(el) {
            var form = el.closest('form[data-path]');
            if (!form) return;
            var inp = paramControl(el);
            var key = paramStorageKey(form, el.getAttribute('data-param-in'), inp.getAttribute('name'));
            var saved = localStorage.getItem(key);
            if (saved !== null) {
                if (inp.type === 'checkbox') inp.checked = saved === 'true';
                else inp.value = saved;
                var persistBtn = inp.closest('.field').querySelector('.persist-toggle');
                if (persistBtn) persistBtn.setAttribute('aria-pressed', 'true');
            }
        });
        // Restore persisted per-op custom headers
        var form = document.querySelector('#detail form[data-path]');
        if (form) {
            var prefix = customHeaderStorageKey(form, '');
            var container = form.querySelector('.custom-headers');
            if (container) {
                var addBtn = container.querySelector('.custom-header-add');
                for (var si = 0; si < localStorage.length; si++) {
                    var sKey = localStorage.key(si);
                    if (!sKey.startsWith(prefix)) continue;
                    var chName = sKey.substring(prefix.length);
                    var chValue = localStorage.getItem(sKey);
                    var chRow = document.createElement('div');
                    chRow.className = 'custom-header-row';
                    chRow.setAttribute('data-prev-name', chName);
                    chRow.innerHTML =
                        '<input type="text" class="input is-small custom-header-name" placeholder="Header name" value="' + chName.replace(/"/g, '&quot;') + '">' +
                        '<div class="control has-icons-right">' +
                        '<input type="text" class="input is-small custom-header-value" placeholder="Value" value="' + chValue.replace(/"/g, '&quot;') + '">' +
                        '<span class="icon is-small is-right persist-toggle" aria-pressed="true" title="Pin value (' + shortcutMod + '+P)"><i class="fa-solid fa-thumbtack"></i></span>' +
                        '</div>' +
                        '<button type="button" class="delete is-small custom-header-remove"></button>';
                    container.insertBefore(chRow, addBtn);
                }
            }
        }
        applyGlobalHeaderPlaceholders();
        document.querySelectorAll('select[data-example-select]').forEach(function(sel) {
            sel.addEventListener('change', function() {
                var textarea = sel.closest('.field').querySelector('textarea[data-request-body]');
                if (textarea) textarea.value = sel.value;
            });
        });
        // Restore session-cached field values and response
        var form = document.querySelector('#detail form[data-path]');
        if (form) {
            var area = form.querySelector('.response-area');
            if (area) initialResponseArea = area.innerHTML;
            restoreFields(form);
            restoreResponse(form);
            restoreSchemaToggles(form);
        }
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

    function randomName() {return 'h-' + Math.random().toString(36).slice(2);}

    function paramStorageKey(form, paramIn, name) {
        if (paramIn === 'path') {
            var pathInputs = Array.from(form.querySelectorAll('[data-param-in="path"]'));
            var paramIndex = 0;
            for (var p = 0; p < pathInputs.length; p++) {
                if (pathInputs[p].getAttribute('name') === name) { paramIndex = p; break; }
            }
            var pathTemplate = form.getAttribute('data-fragment-path');
            var segments = pathTemplate.split('/');
            var positionPath = [];
            var paramCount = 0;
            for (var i = 0; i < segments.length; i++) {
                var isParam = segments[i].charAt(0) === '{';
                if (isParam) {
                    positionPath.push('{}');
                    if (paramCount === paramIndex) break;
                    paramCount++;
                } else {
                    positionPath.push(segments[i]);
                }
            }
            return 'openapi-ui-param:path:' + positionPath.join('/');
        }
        return 'openapi-ui-param:' + paramIn + ':' + form.getAttribute('data-method') + ':' + form.getAttribute('data-path') + ':' + name;
    }

    function customHeaderStorageKey(form, name) {
        return 'openapi-ui-custom-header:' + form.getAttribute('data-method') + ':' + form.getAttribute('data-path') + ':' + name;
    }

    function paramControl(el) {
        var inner = el.querySelector('select');
        return inner || el;
    }

    function paramValue(inp) {
        return inp.type === 'checkbox' ? String(inp.checked) : inp.value;
    }

    function detectLanguage(contentType) {
        var mime = (contentType || '').split(';')[0].trim();
        var subtype = mime.split('/')[1] || '';
        var suffix = subtype.includes('+') ? subtype.split('+').pop() : subtype;
        if (typeof hljs !== 'undefined' && hljs.getLanguage(suffix)) return suffix;
        return null;
    }

    // Spec-defined param persistence
    detail.addEventListener('click', function(e) {
        var persistBtn = e.target.closest('#detail .field .persist-toggle');
        if (!persistBtn) return;
        var pressed = persistBtn.getAttribute('aria-pressed') === 'true';
        persistBtn.setAttribute('aria-pressed', String(!pressed));
        var fieldEl = persistBtn.closest('.field');
        var el = fieldEl.querySelector('[data-param-in]');
        if (!el) return;
        var inp = paramControl(el);
        var form = el.closest('form[data-path]');
        var key = paramStorageKey(form, el.getAttribute('data-param-in'), inp.getAttribute('name'));
        if (!pressed) {
            localStorage.setItem(key, paramValue(inp));
        } else {
            localStorage.removeItem(key);
        }
    });
    detail.addEventListener('change', function(e) {
        // Persisted param value change (checkbox or select)
        var paramEl = e.target.closest('[data-param-in]');
        if (paramEl) {
            var fieldEl = paramEl.closest('.field');
            var persistBtn = fieldEl.querySelector('.persist-toggle');
            if (persistBtn && persistBtn.getAttribute('aria-pressed') === 'true') {
                var inp = paramControl(paramEl);
                var form = paramEl.closest('form[data-path]');
                var key = paramStorageKey(form, paramEl.getAttribute('data-param-in'), inp.getAttribute('name'));
                localStorage.setItem(key, paramValue(inp));
            }
            return;
        }
        // Per-op custom header persistence — handled via click on persist-toggle
    });
    // Per-op custom header persist toggle
    detail.addEventListener('click', function(e) {
        var persistBtn = e.target.closest('.custom-headers .persist-toggle');
        if (!persistBtn) return;
        var pressed = persistBtn.getAttribute('aria-pressed') === 'true';
        persistBtn.setAttribute('aria-pressed', String(!pressed));
        var row = persistBtn.closest('.custom-header-row');
        var form = row.closest('form[data-path]');
        var name = row.querySelector('.custom-header-name').value.trim();
        var value = row.querySelector('.custom-header-value').value;
        var key = customHeaderStorageKey(form, name);
        if (!pressed && name) {
            localStorage.setItem(key, value);
            row.setAttribute('data-prev-name', name);
        } else if (name) {
            localStorage.removeItem(key);
            row.removeAttribute('data-prev-name');
        }
    });
    detail.addEventListener('input', function(e) {
        var el = e.target.closest('[data-param-in]');
        if (el) {
            var fieldEl = el.closest('.field');
            var persistBtn = fieldEl.querySelector('.persist-toggle');
            if (!persistBtn || persistBtn.getAttribute('aria-pressed') !== 'true') return;
            var inp = paramControl(el);
            var form = el.closest('form[data-path]');
            localStorage.setItem(paramStorageKey(form, el.getAttribute('data-param-in'), inp.getAttribute('name')), paramValue(inp));
            return;
        }
        // Per-op custom header input update
        var customInp = e.target.closest('.custom-headers .custom-header-name, .custom-headers .custom-header-value');
        if (customInp) {
            var row = customInp.closest('.custom-header-row');
            var persistBtn = row.querySelector('.persist-toggle');
            if (!persistBtn || persistBtn.getAttribute('aria-pressed') !== 'true') return;
            var form = row.closest('form[data-path]');
            var name = row.querySelector('.custom-header-name').value.trim();
            var value = row.querySelector('.custom-header-value').value;
            var prevName = row.getAttribute('data-prev-name');
            if (prevName && prevName !== name) localStorage.removeItem(customHeaderStorageKey(form, prevName));
            if (name) {
                localStorage.setItem(customHeaderStorageKey(form, name), value);
                row.setAttribute('data-prev-name', name);
            }
        }
    });

    // Save field values to session cache on every input
    detail.addEventListener('input', function(e) {
        var form = e.target.closest('form[data-path]');
        if (form) saveFields(form);
    });

    // Schema box toggle + custom header management
    detail.addEventListener('click', function(e) {
        var addBtn = e.target.closest('.custom-header-add');
        if (addBtn) {
            var container = addBtn.closest('.custom-headers');
            var row = document.createElement('div');
            row.className = 'custom-header-row';
            row.innerHTML =
                '<input type="text" class="input is-small custom-header-name" name="' + randomName() + '" placeholder="Header name">' +
                '<div class="control has-icons-right">' +
                '<input type="text" class="input is-small custom-header-value" name="' + randomName() + '" placeholder="Value">' +
                '<span class="icon is-small is-right persist-toggle" aria-pressed="false" title="Pin value (' + shortcutMod + '+P)"><i class="fa-solid fa-thumbtack"></i></span>' +
                '</div>' +
                '<button type="button" class="delete is-small custom-header-remove"></button>';
            container.insertBefore(row, addBtn);
            row.querySelector('.custom-header-name').focus();
            return;
        }
        var removeBtn = e.target.closest('.custom-header-remove');
        if (removeBtn) {
            var row = removeBtn.closest('.custom-header-row');
            var persistBtn = row.querySelector('.persist-toggle');
            if (persistBtn && persistBtn.getAttribute('aria-pressed') === 'true') {
                var form = row.closest('form[data-path]');
                var name = row.querySelector('.custom-header-name').value.trim();
                if (form && name) localStorage.removeItem(customHeaderStorageKey(form, name));
            }
            row.remove();
            return;
        }
        var toggle = e.target.closest('.schema-toggle');
        if (toggle) {
            var box = toggle.closest('.schema-box');
            box.classList.toggle('is-collapsed');
            toggle.textContent = box.classList.contains('is-collapsed') ? 'Schema ▸' : 'Schema ▾';
            toggle.focus(); // Safari doesn't focus buttons on click
            var form = toggle.closest('form[data-path]');
            if (form) saveSchemaToggles(form);
            return;
        }
        var nestedToggle = e.target.closest('.schema-nested-toggle');
        if (nestedToggle) {
            var expanded = nestedToggle.getAttribute('aria-expanded') === 'true';
            nestedToggle.setAttribute('aria-expanded', expanded ? 'false' : 'true');
            nestedToggle.focus(); // Safari doesn't focus buttons on click
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

    var fragmentCache = new Map();
    var initialResponseArea = null;

    function fragmentBaseUrl(form) {
        var path = form.getAttribute('data-fragment-path');
        var method = form.getAttribute('data-method');
        return path + '/' + method + '-response-';
    }

    function clearPreviousResponse() {
        var area = detail.querySelector('.response-area');
        if (!area) return;
        if (!initialResponseArea) initialResponseArea = area.innerHTML;
        area.innerHTML = initialResponseArea;
    }

    function showResponse(area, form, status, statusText, headers, body, ct, headersExpanded) {
        var base = fragmentBaseUrl(form);
        var specificUrl = base + status + '.html';
        var fallbackUrl = base + 'fallback.html';
        var isFallback = false;

        function populate(html) {
            area.innerHTML = html;
            var badge = area.querySelector('.response-status');
            if (isFallback && badge) {
                badge.textContent = status + ' ' + statusText;
                if (status >= 200 && status < 300) badge.classList.add('is-success');
                else badge.classList.add('is-error');
            }
            // populate documented header values
            if (headers && headers.length > 0) {
                var headersByName = {};
                headers.forEach(function(h) { headersByName[h.name.toLowerCase()] = h.value; });
                area.querySelectorAll('.response-header-value[data-header]').forEach(function(el) {
                    var name = el.getAttribute('data-header');
                    if (headersByName[name] !== undefined) {
                        el.textContent = headersByName[name];
                    } else if (el.hasAttribute('data-required')) {
                        el.textContent = '(missing)';
                        el.classList.add('response-header-missing');
                        var nameEl = el.previousElementSibling;
                        if (nameEl) nameEl.classList.add('response-header-missing');
                    } else {
                        if (name === 'set-cookie') {
                            el.textContent = 'browser sends these automatically, not visible to JS';
                        } else {
                            el.textContent = '\u2014';
                        }
                        el.classList.add('response-header-absent');
                    }
                });
                // append undocumented headers
                var docGrid = area.querySelector('.response-documented-headers');
                var undocGrid = area.querySelector('.response-headers-undocumented');
                if (!undocGrid) {
                    undocGrid = document.createElement('div');
                    undocGrid.className = 'response-header-rows response-headers-undocumented';
                    var headersContainer = area.querySelector('.response-headers');
                    if (headersContainer) headersContainer.appendChild(undocGrid);
                }
                var documentedNames = {};
                area.querySelectorAll('.response-documented-headers .response-header-name').forEach(function(el) {
                    documentedNames[el.textContent.toLowerCase()] = true;
                });
                var hasDocumented = Object.keys(documentedNames).length > 0;
                var undocumented = [];
                headers.forEach(function(h) {
                    if (hasDocumented && documentedNames[h.name.toLowerCase()]) return;
                    if (!hasDocumented) {
                        // no documented headers in fragment — show all in main grid
                        var grid = docGrid || undocGrid;
                        appendHeaderRow(grid, h.name, h.value);
                    } else {
                        undocumented.push(h);
                    }
                });
                undocumented.forEach(function(h) {
                    appendHeaderRow(undocGrid, h.name, h.value);
                });
                // show-all button
                if (hasDocumented && undocumented.length > 0) {
                    undocGrid.style.display = 'none';
                    var showAllBtn = document.createElement('button');
                    showAllBtn.type = 'button';
                    showAllBtn.className = 'response-headers-show-all';
                    showAllBtn.textContent = 'Show all (' + undocumented.length + ' more)';
                    var headersContainer = area.querySelector('.response-headers');
                    headersContainer.insertBefore(showAllBtn, undocGrid);
                }
                // auto-detect: expand if any documented header has an actual value
                if (headersExpanded === null) {
                    var hasMatch = false;
                    area.querySelectorAll('.response-documented-headers .response-header-value[data-header]').forEach(function(el) {
                        if (el.textContent && el.textContent !== '(missing)' && el.textContent !== '\u2014') hasMatch = true;
                    });
                    headersExpanded = hasMatch;
                }
                // update toggle text with count
                var toggle = area.querySelector('.response-headers-toggle');
                if (toggle) {
                    toggle.textContent = 'Headers (' + headers.length + ') ' + (headersExpanded ? '\u25BE' : '\u25B8');
                }
                // expand headers if requested
                var headersContainer = area.querySelector('.response-headers');
                if (headersContainer && headersExpanded) headersContainer.classList.add('is-expanded');
            } else {
                var headersBox = area.querySelector('.response-headers');
                if (headersBox) {
                    var toggle = headersBox.querySelector('.response-headers-toggle');
                    if (toggle) toggle.style.display = 'none';
                    var emptyMsg = headersBox.querySelector('.response-empty');
                    if (emptyMsg) emptyMsg.style.display = '';
                }
            }
            // populate body
            var pre = area.querySelector('pre.response');
            if (body && body.trim()) {
                if (pre) {
                    var lang = detectLanguage(ct);
                    if (lang && typeof hljs !== 'undefined') {
                        var code = document.createElement('code');
                        code.className = 'language-' + lang;
                        code.textContent = body;
                        pre.appendChild(code);
                        hljs.highlightElement(code);
                    } else {
                        pre.textContent = body;
                    }
                }
            } else {
                if (pre) pre.style.display = 'none';
                var emptyBody = area.querySelector('p.response-empty.box');
                if (emptyBody) emptyBody.style.display = '';
            }
        }

        function fetchFragment(url) {
            if (fragmentCache.has(url)) return Promise.resolve(fragmentCache.get(url));
            return fetch(url).then(function(resp) {
                if (!resp.ok) return null;
                return resp.text().then(function(html) {
                    fragmentCache.set(url, html);
                    return html;
                });
            });
        }

        return fetchFragment(specificUrl).then(function(html) {
            if (html) return populate(html);
            isFallback = true;
            return fetchFragment(fallbackUrl).then(function(html) {
                if (html) populate(html);
            });
        });
    }

    function appendHeaderRow(grid, name, value) {
        var nameEl = document.createElement('span');
        nameEl.className = 'response-header-name';
        nameEl.textContent = name;
        var valueEl = document.createElement('span');
        valueEl.className = 'response-header-value';
        valueEl.setAttribute('data-header', name.toLowerCase());
        valueEl.textContent = value;
        grid.appendChild(nameEl);
        grid.appendChild(valueEl);
    }

    // event delegation for response-area toggle/show-all clicks
    detail.addEventListener('keydown', function(e) {
        if (e.key !== 'Enter' && e.key !== ' ') return;
        var toggle = e.target.closest('.response-headers-toggle');
        if (toggle) {
            e.preventDefault();
            toggle.click();
        }
    });
    detail.addEventListener('click', function(e) {
        var toggle = e.target.closest('.response-headers-toggle');
        if (toggle) {
            var area = toggle.closest('.response-area');
            if (!area) return;
            var container = toggle.closest('.response-headers');
            if (container) {
                var expanded = container.classList.toggle('is-expanded');
                var count = toggle.textContent.match(/\((\d+)\)/);
                var num = count ? count[1] : '';
                toggle.textContent = 'Headers' + (num ? ' (' + num + ') ' : ' ') + (expanded ? '\u25BE' : '\u25B8');
            }
            return;
        }
        var showAll = e.target.closest('.response-headers-show-all');
        if (showAll) {
            var area = showAll.closest('.response-area');
            if (!area) return;
            var undocGrid = area.querySelector('.response-headers-undocumented');
            if (undocGrid) {
                var visible = undocGrid.style.display !== 'none';
                undocGrid.style.display = visible ? 'none' : '';
                var count = showAll.textContent.match(/\((\d+)/);
                showAll.textContent = visible
                    ? 'Show all (' + (count ? count[1] : '') + ' more)'
                    : 'Show less';
            }
            return;
        }
    });

    function showCopied(btn) {
        var original = btn.textContent;
        btn.textContent = 'Copied!';
        btn.focus();
        setTimeout(function() { btn.textContent = original; }, 1500);
    }

    // Pin shortcut (Ctrl+P / Alt+P) — works in both detail pane and global headers
    document.addEventListener('keydown', function(e) {
        var persistMod = /Mac/.test(navigator.platform) ? e.ctrlKey : e.altKey;
        if (e.code === 'KeyP' && persistMod) {
            var el = document.activeElement;
            var fieldEl = el.closest('.field');
            var persistBtn = fieldEl ? fieldEl.querySelector('.persist-toggle') : null;
            if (!persistBtn) {
                var row = el.closest('.custom-header-row');
                persistBtn = row ? row.querySelector('.persist-toggle') : null;
            }
            if (persistBtn) {
                persistBtn.click();
                e.preventDefault();
                e.stopPropagation();
            }
        }
    });

    function findSpatialTarget(el, direction) {
        var rect = el.getBoundingClientRect();
        var cx = rect.left + rect.width / 2;
        var cy = rect.top + rect.height / 2;
        var candidates = Array.from(document.querySelectorAll(
            '#method-content input, #method-content select, #method-content textarea, #method-content button, #method-content [tabindex="0"],'
            + ' .tabs a[tabindex="0"],'
            + ' [role="tree"]'
        ));
        var elIsTab = el.closest && el.closest('.tabs');
        var elIsStatusTab = el.classList.contains('schema-status-tab');
        candidates = candidates.filter(function(c) {
            if (c === el) return false;
            if (c.disabled) return false;
            if (!c.offsetParent && c.getAttribute('role') !== 'tree') return false;
            if (c.closest && c.closest('.toggle')) return false;
            // when navigating from outside tabs, only the active tab is a candidate
            if (!elIsTab && c.closest && c.closest('.tabs') && !c.closest('li').classList.contains('is-active')) return false;
            if (!elIsStatusTab && c.classList.contains('schema-status-tab') && !c.classList.contains('is-active')) return false;
            return true;
        });

        var best = null;
        var bestDist = Infinity;
        var bestCorridor = false;

        candidates.forEach(function(c) {
            var cr = c.getBoundingClientRect();
            // for tab links, use the tab bar's rect so the whole bar acts as one spatial unit
            if (!elIsTab && c.closest && c.closest('.tabs')) cr = c.closest('.tabs').getBoundingClientRect();
            if (!elIsStatusTab && c.classList.contains('schema-status-tab')) cr = c.closest('.schema-status-tabs').getBoundingClientRect();
            var ccx = cr.left + cr.width / 2;
            var ccy = cr.top + cr.height / 2;

            // filter by direction
            if (direction === 'down' && ccy <= cy) return;
            if (direction === 'up' && ccy >= cy) return;
            if (direction === 'right' && ccx <= cx) return;
            if (direction === 'left' && ccx >= cx) return;

            // check corridor overlap on cross-axis
            var corridor;
            if (direction === 'down' || direction === 'up') {
                corridor = rect.right > cr.left && cr.right > rect.left; // horizontal overlap
            } else {
                corridor = rect.bottom > cr.top && cr.bottom > rect.top; // vertical overlap
            }

            // distance: primary axis for corridor, euclidean for fallback
            var dist;
            if (corridor) {
                dist = (direction === 'down' || direction === 'up') ? Math.abs(ccy - cy) : Math.abs(ccx - cx);
            } else {
                dist = Math.sqrt((ccx - cx) * (ccx - cx) + (ccy - cy) * (ccy - cy));
            }

            // corridor candidates beat non-corridor
            if (corridor && !bestCorridor) {
                best = c;
                bestDist = dist;
                bestCorridor = true;
            } else if (corridor === bestCorridor && dist < bestDist) {
                best = c;
                bestDist = dist;
            }
        });

        return best;
    }

    // Spatial arrow-key navigation for detail pane and tabs
    document.addEventListener('keydown', function(e) {
        var el = document.activeElement;
        if (!el || !el.closest) return;
        // don't interfere with tree or toggle internal navigation
        if (el.closest('[role="tree"]')) return;
        if (el.closest('.toggle')) return;

        var isArrow = ['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].indexOf(e.key) >= 0;
        if (!isArrow && e.key !== 'Enter' && e.key !== 'Escape') return;

        // text input/textarea: Left/Right stay native
        if ((e.key === 'ArrowLeft' || e.key === 'ArrowRight')
            && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) return;

        // textarea multi-line: Up/Down navigate away only at first/last line
        if (el.tagName === 'TEXTAREA' && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
            var val = el.value;
            var pos = el.selectionStart;
            if (e.key === 'ArrowDown') {
                if (val.indexOf('\n', pos) >= 0) return; // not at last line
            } else {
                if (val.lastIndexOf('\n', pos - 1) >= 0) return; // not at first line
            }
        }

        if (isArrow) {
            var direction = e.key === 'ArrowDown' ? 'down'
                : e.key === 'ArrowUp' ? 'up'
                : e.key === 'ArrowRight' ? 'right' : 'left';
            var target = findSpatialTarget(el, direction);
            if (target) {
                target.focus();
            } else {
                var bumpDir = (direction === 'left' || direction === 'right') ? 'h' : 'v';
                bump(el, bumpDir);
            }
            e.preventDefault();
            e.stopPropagation();
        } else if (e.key === 'Enter') {
            if (el.classList.contains('schema-toggle')) {
                el.click();
            } else if (el.tagName !== 'SELECT') {
                var mc = document.getElementById('method-content') || document.getElementById('detail');
                var sendBtn = mc ? mc.querySelector('button[type=submit]') : null;
                if (sendBtn) sendBtn.click();
            }
            e.preventDefault();
            e.stopPropagation();
        } else if (e.key === 'Escape') {
            var tree = document.querySelector('[role="tree"]');
            if (tree) tree.focus();
            e.preventDefault();
            e.stopPropagation();
        }
    }, true);

    // Activate method tabs on focus (spatial navigation focuses them)
    document.addEventListener('focusin', function(e) {
        var link = e.target.closest('.tabs a');
        if (!link) return;
        var li = link.closest('li');
        if (!li || li.classList.contains('is-active')) return;
        var tabs = li.closest('.tabs');
        tabs.querySelectorAll('li').forEach(function(l) { l.classList.remove('is-active'); });
        li.classList.add('is-active');
        clearPreviousResponse();
        var hxGet = link.getAttribute('hx-get');
        htmx.ajax('GET', hxGet, link.getAttribute('hx-target'));
        history.replaceState(null, '', '#' + hxGetToRoute(hxGet));
    });

    // Activate status code tabs on focus
    document.addEventListener('focusin', function(e) {
        if (!e.target.classList.contains('schema-status-tab')) return;
        if (e.target.classList.contains('is-active')) return;
        activateStatusTab(e.target);
    });

    // When clicking a method badge in the tree, navigate to that tab
    var pendingMethod = null;
    document.addEventListener('click', function(e) {
        var badge = e.target.closest('[role="treeitem"] .tag');
        if (!badge) return;
        pendingMethod = badge.textContent.trim();
    }, true);

    // Navigate from URL hash or auto-load first operation
    var HTTP_METHODS = ['GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS','TRACE'];
    window._hashNavPending = false;
    function navigateFromHash() {
        var route = decodeURIComponent(location.hash.replace(/^#/, ''));
        if (!route) return false;
        // Parse [tag] prefix for tag tree navigation
        var tagFromHash = null;
        var tagMatch = route.match(/^\[([^\]]+)\](.*)/);
        if (tagMatch) {
            tagFromHash = tagMatch[1];
            route = tagMatch[2];
        }
        var parts = route.split('/');
        var last = parts[parts.length - 1];
        var methodFromHash = null;
        var treePath = route;
        if (HTTP_METHODS.indexOf(last) >= 0) {
            methodFromHash = last;
            treePath = parts.slice(0, -1).join('/');
        }
        var hxGet, hxEl;
        if (tagFromHash) {
            // Tag tree: hx-get is "path/METHOD.html", find by data-tag + hx-get
            hxGet = route + '.html';
            hxEl = document.querySelector('[data-tag="' + tagFromHash + '"][hx-get="' + hxGet + '"]');
            if (!hxEl) {
                // Tag tree may not be loaded yet — switch to tags view; afterSettle will retry
                window._hashNavPending = true;
                var vt = document.querySelector('[data-toggle="view"]');
                if (vt) {
                    var tagsBtn = document.querySelector('[data-toggle-value="tags"]');
                    if (tagsBtn && !tagsBtn.classList.contains('is-active')) vt._select('tags');
                }
                return true;
            }
        } else {
            // Path tree: hx-get is "path/index.html"
            hxGet = treePath + '/index.html';
            hxEl = document.querySelector('[hx-get="' + hxGet + '"]');
            if (!hxEl) return false;
        }
        var item = hxEl.closest('[role="treeitem"]');
        var tree = document.querySelector('[role="tree"]');
        if (item && tree) {
            tree._expandParentsOf(item);
            tree.querySelectorAll('[aria-selected="true"]').forEach(function(el) { el.removeAttribute('aria-selected'); });
            item.setAttribute('aria-selected', 'true');
        }
        if (methodFromHash && !tagFromHash) pendingMethod = methodFromHash;
        htmx.ajax('GET', hxGet, {target: '#detail', swap: 'innerHTML'});
        return true;
    }
    if (!navigateFromHash()) {
        var firstHxEl = document.querySelector('#tree-container [hx-get]');
        if (firstHxEl) {
            var hxGet = firstHxEl.getAttribute('hx-get');
            htmx.ajax('GET', hxGet, '#detail');
            var route = hxGetToRoute(hxGet);
            var tag = firstHxEl.getAttribute('data-tag');
            var hash = tag ? '#[' + tag + ']' + route : '#' + route;
            history.replaceState(null, '', hash);
        }
    }
    window.addEventListener('popstate', function() { navigateFromHash(); });

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
            var requestHeaders = {};
            var cookieParts = [];
            inputs.forEach(function(inp) {
                var name = inp.getAttribute('name');
                var paramIn = inp.getAttribute('data-param-in') || 'query';
                var isCheckbox = inp.type === 'checkbox';
                var val = isCheckbox ? (inp.checked ? 'true' : '') : inp.value;
                if (paramIn === 'path' || pathTemplate.includes('{' + name + '}')) {
                    resolvedPath = resolvedPath.replace('{' + name + '}', encodeURIComponent(val));
                } else if (paramIn === 'header') {
                    if (val) requestHeaders[name] = val;
                } else if (paramIn === 'cookie') {
                    if (val) cookieParts.push(name + '=' + val);
                } else if (val) {
                    queryParams.push(name + '=' + encodeURIComponent(val));
                }
            });
            if (cookieParts.length > 0) requestHeaders['Cookie'] = cookieParts.join('; ');
            sendForm.querySelectorAll('.custom-header-row').forEach(function(row) {
                var name = row.querySelector('.custom-header-name').value.trim();
                var value = row.querySelector('.custom-header-value').value;
                if (name) requestHeaders[name] = value;
            });
            // Collect global headers (lowest priority) and merge
            var globalHeaders = {};
            var ghPanel = document.getElementById('global-headers');
            if (ghPanel) {
                ghPanel.querySelectorAll('.custom-header-row').forEach(function(row) {
                    var name = row.querySelector('.custom-header-name').value.trim();
                    var value = row.querySelector('.custom-header-value').value;
                    if (name) globalHeaders[name] = value;
                });
            }
            var mergedHeaders = {};
            Object.keys(globalHeaders).forEach(function(h) { mergedHeaders[h] = globalHeaders[h]; });
            Object.keys(requestHeaders).forEach(function(h) { mergedHeaders[h] = requestHeaders[h]; });
            requestHeaders = mergedHeaders;
            var url = baseUrl.startsWith('http') ? baseUrl + resolvedPath
                    : new URL((baseUrl + resolvedPath).replace(/\/+/g, '/'), window.location.origin).href;
            if (queryParams.length > 0) url += '?' + queryParams.join('&');

            var bodyTextarea = sendForm.querySelector('textarea[data-request-body]');
            var bodyValue = bodyTextarea ? bodyTextarea.value : '';

            if (mode === 'curl') {
                var headerFlags = Object.keys(requestHeaders).filter(function(h) {
                    return h !== 'Cookie';
                }).map(function(h) {
                    return "-H '" + h + ": " + requestHeaders[h] + "'";
                }).join(' ');
                var cmd = 'curl -X ' + method;
                if (headerFlags) cmd += ' ' + headerFlags;
                if (cookieParts.length > 0) cmd += " -b '" + cookieParts.join('; ') + "'";
                if (bodyValue) cmd += " -H 'Content-Type: application/json' -d '" + bodyValue + "'";
                cmd += ' ' + url;
                navigator.clipboard.writeText(cmd);
                showCopied(sendBtn);
            } else if (mode === 'httpie') {
                var headerArgs = Object.keys(requestHeaders).filter(function(h) {
                    return h !== 'Cookie';
                }).map(function(h) {
                    return h + ':' + requestHeaders[h];
                }).join(' ');
                var cmd = bodyValue
                        ? "echo '" + bodyValue + "' | http " + method + ' ' + url + " Content-Type:application/json"
                        : 'http ' + method + ' ' + url;
                if (headerArgs) cmd += ' ' + headerArgs;
                if (cookieParts.length > 0) cmd += ' Cookie:' + cookieParts.join('\\; ');
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
                Object.keys(requestHeaders).forEach(function(h) {
                    if (h !== 'Cookie') fetchOptions.headers[h] = requestHeaders[h];
                });
                var area = sendForm.querySelector('.response-area');
                fetch(url, fetchOptions).then(function(resp) {
                    var ct = resp.headers.get('Content-Type') || '';
                    var headers = [];
                    resp.headers.forEach(function(value, name) {
                        headers.push({name: name, value: value});
                    });
                    return resp.text().then(function(text) {
                        var headersWereVisible = area.querySelector('.response-headers.is-expanded') !== null;
                        if (!area.querySelector('.response-headers')) headersWereVisible = null; // auto-detect
                        if (ct.includes('json')) {
                            try { text = JSON.stringify(JSON.parse(text), null, 2); } catch(e) {}
                        } else if (ct.includes('xml')) {
                            try { text = prettyPrintXml(text); } catch(e) {}
                        }
                        return showResponse(area, sendForm, resp.status, resp.statusText, headers, text, ct, headersWereVisible).then(function() {
                            var currentlyVisible = area.querySelector('.response-headers.is-expanded') !== null;
                            responseCache.set(opKey(sendForm), {
                                status: resp.status, statusText: resp.statusText,
                                headers: headers, headersExpanded: currentlyVisible,
                                body: text, ct: ct
                            });
                        });
                    });
                }).catch(function(err) {
                    return showResponse(area, sendForm, 0, 'Network error', [], err.message, '', false);
                }).finally(function() {
                    var btn = area.querySelector('button[type=submit]');
                    if (btn) {
                        btn.disabled = false;
                        btn.textContent = 'Send';
                        btn.focus();
                    }
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
