// ════════════════════════════════════════════════════════════════════════════
// AZURE RESOURCE MANAGER DASHBOARD - JavaScript
// ════════════════════════════════════════════════════════════════════════════

// Intervalo para verificar status de deleção
let statusCheckInterval = null;

// Registro de grupos já notificados (evita toasts duplicados)
const notifiedGroups = new Set();

// ════════════════════════════════════════════════════════════════════════════
// NAVEGAÇÃO DE ASSINATURAS
// ════════════════════════════════════════════════════════════════════════════

function switchSubscription(element) {
    const subscriptionId = element.getAttribute('data-subscription-id');
    console.log('Carregando recursos da assinatura:', subscriptionId);
    
    showLoading();
    
    document.querySelectorAll('.subscription-item').forEach(item => {
        item.classList.remove('subscription-active');
    });
    element.classList.add('subscription-active');
    
    setTimeout(() => {
        window.location.href = '?subscriptionId=' + encodeURIComponent(subscriptionId);
    }, 300);
}

// ════════════════════════════════════════════════════════════════════════════
// EXPANSÃO DE RECURSOS
// ════════════════════════════════════════════════════════════════════════════

function toggleResources(element) {
    const card = element.closest('.resource-group-card');
    const resourcesList = card.querySelector('.resources-list');
    const badge = element.querySelector('.resource-count-badge');
    
    if (resourcesList.style.display === 'none') {
        resourcesList.style.display = 'block';
        badge.style.backgroundColor = '#ff9500';
        badge.style.color = 'white';
    } else {
        resourcesList.style.display = 'none';
        badge.style.backgroundColor = '';
        badge.style.color = '';
    }
}

// ════════════════════════════════════════════════════════════════════════════
// MODAL DELETAR TUDO COM FILTRO DE EXCLUSÃO
// ════════════════════════════════════════════════════════════════════════════

function openDeleteAllModal() {
    const modal = document.getElementById('delete-all-modal');
    modal.style.display = 'flex';
    
    // Limpa campos anteriores
    document.getElementById('exclude-groups').value = '';
    document.getElementById('preview-section').style.display = 'none';
    document.querySelector('input[name="match-mode"][value="exact"]').checked = true;
    
    // Adiciona listener para preview em tempo real
    document.getElementById('exclude-groups').addEventListener('input', updateExclusionPreview);
    document.querySelectorAll('input[name="match-mode"]').forEach(radio => {
        radio.addEventListener('change', updateExclusionPreview);
    });
}

function closeDeleteAllModal() {
    const modal = document.getElementById('delete-all-modal');
    modal.style.display = 'none';
}

function getExcludePatterns() {
    const text = document.getElementById('exclude-groups').value.trim();
    if (!text) return [];
    
    // Separa por linha ou vírgula
    return text.split(/[\n,]+/)
               .map(s => s.trim())
               .filter(s => s.length > 0);
}

function getMatchMode() {
    return document.querySelector('input[name="match-mode"]:checked').value;
}

function shouldExcludeGroup(groupName, patterns, matchMode) {
    const lowerGroupName = groupName.toLowerCase();
    
    for (const pattern of patterns) {
        const lowerPattern = pattern.toLowerCase();
        
        switch (matchMode) {
            case 'exact':
                if (lowerGroupName === lowerPattern) return true;
                break;
            case 'contains':
                if (lowerGroupName.includes(lowerPattern)) return true;
                break;
            case 'startsWith':
                if (lowerGroupName.startsWith(lowerPattern)) return true;
                break;
        }
    }
    return false;
}

function updateExclusionPreview() {
    const patterns = getExcludePatterns();
    const matchMode = getMatchMode();
    const previewSection = document.getElementById('preview-section');
    const previewList = document.getElementById('preview-excluded');
    
    if (patterns.length === 0) {
        previewSection.style.display = 'none';
        return;
    }
    
    // Coleta todos os grupos de todas as assinaturas visíveis
    const allGroups = Array.from(document.querySelectorAll('.resource-group-card'))
        .map(card => card.getAttribute('data-group-name'))
        .filter(name => name);
    
    const excludedGroups = allGroups.filter(name => shouldExcludeGroup(name, patterns, matchMode));
    
    if (excludedGroups.length > 0) {
        previewSection.style.display = 'block';
        previewList.innerHTML = excludedGroups
            .map(name => `<span class="preview-tag">🛡️ ${name}</span>`)
            .join('');
    } else {
        previewSection.style.display = 'block';
        previewList.innerHTML = '<span class="preview-none">Nenhum grupo corresponde aos filtros</span>';
    }
}

function confirmDeleteAll() {
    const patterns = getExcludePatterns();
    const matchMode = getMatchMode();
    
    let message = '⚠️ ATENÇÃO CRÍTICA!\n\nTem certeza que deseja deletar TODOS os grupos de TODAS as assinaturas?';
    
    if (patterns.length > 0) {
        message += `\n\n🛡️ Grupos que SERÃO PRESERVADOS (modo: ${matchMode}):\n- ${patterns.join('\n- ')}`;
    }
    
    message += '\n\nEsta ação é IRREVERSÍVEL!';
    
    if (!confirm(message)) {
        return;
    }
    
    const confirmText = prompt('Digite "CONFIRMAR" para prosseguir:');
    if (confirmText !== 'CONFIRMAR') {
        showToast('Operação cancelada', 'info');
        return;
    }
    
    closeDeleteAllModal();
    executeDeleteAll(patterns, matchMode);
}

function executeDeleteAll(excludePatterns, matchMode) {
    const subscriptionIds = Array.from(document.querySelectorAll('.subscription-item'))
        .map(item => item.getAttribute('data-subscription-id'))
        .filter(id => id);
    
    if (subscriptionIds.length === 0) {
        showToast('Nenhuma assinatura encontrada', 'error');
        return;
    }
    
    // Marca cards visíveis como deletando (exceto os excluídos)
    document.querySelectorAll('.resource-group-card').forEach(card => {
        const name = card.getAttribute('data-group-name');
        if (name && !shouldExcludeGroup(name, excludePatterns, matchMode)) {
            markCardAsDeleting(name);
            notifiedGroups.delete(name);
        }
    });
    
    const requestBody = {
        subscriptionIds: subscriptionIds,
        excludePatterns: excludePatterns,
        matchMode: matchMode
    };
    
    fetch('/api/delete-all-all', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(requestBody)
    })
    .then(r => r.json())
    .then(d => {
        if (d.status === 'started') {
            let msg = 'Deletando grupos em segundo plano...';
            if (excludePatterns.length > 0) {
                msg = `Deletando grupos (exceto ${excludePatterns.length} filtro(s))...`;
            }
            showToast(msg, 'info');
            startStatusPolling();
        } else {
            showToast('Erro: ' + d.message, 'error');
        }
    })
    .catch(e => {
        console.error('Erro:', e);
        showToast('Erro ao deletar grupos', 'error');
    });
}

// ════════════════════════════════════════════════════════════════════════════
// DELEÇÃO ASSÍNCRONA
// ════════════════════════════════════════════════════════════════════════════

function deleteGroupFromButton(button) {
    const groupName = button.getAttribute('data-group-name');
    if (groupName) {
        deleteGroup(groupName);
    }
}

function deleteGroup(groupName) {
    const subscriptionId = getCurrentSubscriptionId();
    if (!subscriptionId) {
        showToast('Nenhuma assinatura selecionada', 'error');
        return;
    }

    if (confirm(`Tem certeza que deseja deletar o grupo "${groupName}"?`)) {
        markCardAsDeleting(groupName);
        notifiedGroups.delete(groupName);
        
        fetch('/api/delete-group?groupName=' + encodeURIComponent(groupName) + '&subscriptionId=' + encodeURIComponent(subscriptionId), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
        })
        .then(r => r.json())
        .then(d => {
            if (d.status === 'started') {
                showToast(`Deletando "${groupName}" em segundo plano...`, 'info');
                startStatusPolling();
            } else {
                showToast('Erro: ' + d.message, 'error');
                unmarkCardAsDeleting(groupName);
            }
        })
        .catch(e => {
            console.error('Erro:', e);
            showToast('Erro ao iniciar deleção', 'error');
            unmarkCardAsDeleting(groupName);
        });
    }
}

function deleteSelectedGroups() {
    const checkboxes = document.querySelectorAll('.group-checkbox:checked');
    if (checkboxes.length === 0) {
        showToast('Nenhum grupo selecionado', 'error');
        return;
    }
    
    const subscriptionId = getCurrentSubscriptionId();
    if (!subscriptionId) {
        showToast('Nenhuma assinatura selecionada', 'error');
        return;
    }

    const groups = Array.from(checkboxes).map(cb => cb.value);
    
    if (confirm(`Tem certeza que deseja deletar ${groups.length} grupo(s)?`)) {
        groups.forEach(g => {
            markCardAsDeleting(g);
            notifiedGroups.delete(g);
        });
        
        fetch('/api/delete-groups', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                groupNames: groups,
                subscriptionId: subscriptionId
            })
        })
        .then(r => r.json())
        .then(d => {
            if (d.status === 'started') {
                showToast(`Deletando ${groups.length} grupo(s) em segundo plano...`, 'info');
                startStatusPolling();
            } else {
                showToast('Erro: ' + d.message, 'error');
                groups.forEach(g => unmarkCardAsDeleting(g));
            }
        })
        .catch(e => {
            console.error('Erro:', e);
            showToast('Erro ao deletar grupos', 'error');
            groups.forEach(g => unmarkCardAsDeleting(g));
        });
    }
}

function deleteAllSubscription() {
    const subscriptionId = getCurrentSubscriptionId();
    if (!subscriptionId) {
        showToast('Nenhuma assinatura selecionada', 'error');
        return;
    }

    if (confirm('⚠️ ATENÇÃO! Tem certeza que deseja deletar TODOS os grupos desta assinatura?')) {
        document.querySelectorAll('.resource-group-card').forEach(card => {
            const name = card.getAttribute('data-group-name');
            if (name) {
                markCardAsDeleting(name);
                notifiedGroups.delete(name);
            }
        });
        
        fetch('/api/delete-all-subscription?subscriptionId=' + encodeURIComponent(subscriptionId), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
        })
        .then(r => r.json())
        .then(d => {
            if (d.status === 'started') {
                showToast('Deletando todos os grupos em segundo plano...', 'info');
                startStatusPolling();
            } else {
                showToast('Erro: ' + d.message, 'error');
            }
        })
        .catch(e => {
            console.error('Erro:', e);
            showToast('Erro ao deletar grupos', 'error');
        });
    }
}

// ════════════════════════════════════════════════════════════════════════════
// MARCAÇÃO VISUAL DE CARDS
// ════════════════════════════════════════════════════════════════════════════

function markCardAsDeleting(groupName) {
    const card = document.querySelector(`[data-group-name="${groupName}"]`);
    if (card && !card.classList.contains('card-deleting')) {
        card.classList.add('card-deleting');
        
        if (!card.querySelector('.deleting-overlay')) {
            const overlay = document.createElement('div');
            overlay.className = 'deleting-overlay';
            overlay.innerHTML = '<div class="deleting-spinner"></div><span>Excluindo...</span>';
            card.insertBefore(overlay, card.firstChild);
        }
        
        const checkbox = card.querySelector('.group-checkbox');
        const deleteBtn = card.querySelector('.btn-delete-single');
        if (checkbox) checkbox.disabled = true;
        if (deleteBtn) deleteBtn.disabled = true;
    }
}

function unmarkCardAsDeleting(groupName) {
    const card = document.querySelector(`[data-group-name="${groupName}"]`);
    if (card) {
        card.classList.remove('card-deleting');
        const overlay = card.querySelector('.deleting-overlay');
        if (overlay) overlay.remove();
        
        const checkbox = card.querySelector('.group-checkbox');
        const deleteBtn = card.querySelector('.btn-delete-single');
        if (checkbox) checkbox.disabled = false;
        if (deleteBtn) deleteBtn.disabled = false;
    }
}

function removeCard(groupName) {
    const card = document.querySelector(`[data-group-name="${groupName}"]`);
    if (card) {
        card.style.animation = 'fadeOut 0.5s ease forwards';
        setTimeout(() => {
            card.remove();
            updateGroupCount();
        }, 500);
    }
}

function updateGroupCount() {
    const count = document.querySelectorAll('.resource-group-card').length;
    const badge = document.querySelector('.content-header .badge-count');
    if (badge) badge.textContent = count;
}

// ════════════════════════════════════════════════════════════════════════════
// POLLING DE STATUS
// ════════════════════════════════════════════════════════════════════════════

function startStatusPolling() {
    if (statusCheckInterval) return;
    
    statusCheckInterval = setInterval(() => {
        checkDeletionStatus();
    }, 3000);
    
    setTimeout(checkDeletionStatus, 1000);
}

function stopStatusPolling() {
    if (statusCheckInterval) {
        clearInterval(statusCheckInterval);
        statusCheckInterval = null;
    }
}

function checkDeletionStatus() {
    fetch('/api/deletion-status')
        .then(r => r.json())
        .then(statusMap => {
            let hasDeleting = false;
            
            for (const [groupName, status] of Object.entries(statusMap)) {
                if (status.status === 'DELETING') {
                    hasDeleting = true;
                    markCardAsDeleting(groupName);
                } else if (status.status === 'COMPLETED') {
                    if (!notifiedGroups.has(groupName)) {
                        notifiedGroups.add(groupName);
                        showToast(`✅ "${groupName}" deletado com sucesso!`, 'success');
                        removeCard(groupName);
                    }
                } else if (status.status === 'FAILED') {
                    if (!notifiedGroups.has(groupName)) {
                        notifiedGroups.add(groupName);
                        showToast(`❌ Erro ao deletar "${groupName}": ${status.message}`, 'error');
                        unmarkCardAsDeleting(groupName);
                    }
                }
            }
            
            if (!hasDeleting) {
                stopStatusPolling();
            }
        })
        .catch(e => {
            console.error('Erro ao verificar status:', e);
        });
}

// ════════════════════════════════════════════════════════════════════════════
// TOAST NOTIFICATIONS
// ════════════════════════════════════════════════════════════════════════════

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️';
    toast.innerHTML = `<span>${icon}</span><span>${message}</span>`;
    
    container.appendChild(toast);
    
    setTimeout(() => {
        toast.classList.add('hiding');
        setTimeout(() => toast.remove(), 300);
    }, 5000);
}

// ════════════════════════════════════════════════════════════════════════════
// UTILITÁRIOS
// ════════════════════════════════════════════════════════════════════════════

function getCurrentSubscriptionId() {
    const hidden = document.getElementById('currentSubscriptionId');
    if (hidden && hidden.value) return hidden.value;
    return new URLSearchParams(window.location.search).get('subscriptionId');
}

function selectAllGroups() {
    document.querySelectorAll('.group-checkbox:not(:disabled)').forEach(cb => {
        cb.checked = true;
    });
    updateSelectedCount();
}

function deselectAllGroups() {
    document.querySelectorAll('.group-checkbox').forEach(cb => {
        cb.checked = false;
    });
    updateSelectedCount();
}

function updateSelectedCount() {
    const count = document.querySelectorAll('.group-checkbox:checked').length;
    console.log(`Grupos selecionados: ${count}`);
}

function refreshData() {
    showLoading();
    setTimeout(() => {
        location.reload();
    }, 300);
}

function showLoading() {
    const loadingMsg = document.getElementById('loading-message');
    if (loadingMsg) {
        loadingMsg.style.display = 'flex';
    }
}

function hideLoading() {
    const loadingMsg = document.getElementById('loading-message');
    if (loadingMsg) {
        loadingMsg.style.display = 'none';
    }
}

// ════════════════════════════════════════════════════════════════════════════
// INICIALIZAÇÃO
// ════════════════════════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', function() {
    hideLoading();
    
    const deletingCards = document.querySelectorAll('.card-deleting');
    if (deletingCards.length > 0) {
        startStatusPolling();
    }
    
    // Fechar modal ao clicar fora
    document.getElementById('delete-all-modal')?.addEventListener('click', function(e) {
        if (e.target === this) {
            closeDeleteAllModal();
        }
    });
    
    // Fechar modal com ESC
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            closeDeleteAllModal();
        }
    });
    
    const style = document.createElement('style');
    style.textContent = `
        @keyframes fadeOut {
            from { opacity: 1; transform: scale(1); }
            to { opacity: 0; transform: scale(0.8); }
        }
    `;
    document.head.appendChild(style);
});
