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

function deleteGroupFromButton(button) {
    const groupName = button.getAttribute('data-group-name');
    if (groupName) {
        deleteGroup(groupName);
    }
}

function deleteGroup(groupName) {
    const subscriptionId = new URLSearchParams(window.location.search).get('subscriptionId');
    if (!subscriptionId) {
        alert('Nenhuma assinatura selecionada');
        return;
    }

    if (confirm(`Tem certeza que deseja deletar o grupo "${groupName}"?`)) {
        showLoading();
        fetch('/api/delete-group?groupName=' + encodeURIComponent(groupName) + '&subscriptionId=' + encodeURIComponent(subscriptionId), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
        })
        .then(r => r.json())
        .then(d => {
            if (d.status === 'success') {
                alert('Grupo deletado com sucesso!');
                location.reload();
            } else {
                hideLoading();
                alert('Erro: ' + d.message);
            }
        })
        .catch(e => {
            hideLoading();
            console.error('Erro:', e);
            alert('Erro ao deletar grupo');
        });
    }
}

function deleteSelectedGroups() {
    const checkboxes = document.querySelectorAll('.group-checkbox:checked');
    if (checkboxes.length === 0) {
        alert('Nenhum grupo selecionado');
        return;
    }
    
    const subscriptionId = new URLSearchParams(window.location.search).get('subscriptionId');
    if (!subscriptionId) {
        alert('Nenhuma assinatura selecionada');
        return;
    }

    const groups = Array.from(checkboxes).map(cb => cb.value);
    
    if (confirm(`Tem certeza que deseja deletar ${groups.length} grupo(s)?`)) {
        showLoading();
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
            if (d.status === 'success') {
                alert(`${groups.length} grupo(s) deletado(s) com sucesso!`);
                location.reload();
            } else {
                hideLoading();
                alert('Erro: ' + d.message);
            }
        })
        .catch(e => {
            hideLoading();
            console.error('Erro:', e);
            alert('Erro ao deletar grupos');
        });
    }
}

function deleteAllSubscription() {
    const subscriptionId = new URLSearchParams(window.location.search).get('subscriptionId');
    if (!subscriptionId) {
        alert('Nenhuma assinatura selecionada');
        return;
    }

    if (confirm('⚠️ ATENÇÃO! Tem certeza que deseja deletar TODOS os grupos desta assinatura? Esta ação não pode ser desfeita!')) {
        showLoading();
        fetch('/api/delete-all-subscription?subscriptionId=' + encodeURIComponent(subscriptionId), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
        })
        .then(r => r.json())
        .then(d => {
            if (d.status === 'success') {
                alert('Todos os grupos deletados com sucesso!');
                location.reload();
            } else {
                hideLoading();
                alert('Erro: ' + d.message);
            }
        })
        .catch(e => {
            hideLoading();
            console.error('Erro:', e);
            alert('Erro ao deletar grupos');
        });
    }
}

function deleteAllAllSubscriptions() {
    if (confirm('⚠️ ATENÇÃO CRÍTICA! Tem certeza que deseja deletar TODOS os grupos de TODAS as assinaturas?\n\nEsta ação é IRREVERSÍVEL e afetará TODAS as assinaturas listadas!')) {
        
        const confirmText = prompt('Digite "CONFIRMAR TUDO" para confirmar esta ação perigosa:');
        if (confirmText !== 'CONFIRMAR TUDO') {
            alert('Operação cancelada');
            return;
        }

        showLoading();
        
        const subscriptionIds = Array.from(document.querySelectorAll('.subscription-item'))
            .map(item => item.getAttribute('data-subscription-id'));
        
        if (subscriptionIds.length === 0) {
            hideLoading();
            alert('Nenhuma assinatura encontrada');
            return;
        }

        fetch('/api/delete-all-all', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(subscriptionIds)
        })
        .then(r => r.json())
        .then(d => {
            if (d.status === 'success') {
                alert('✅ TODOS os grupos de TODAS as assinaturas foram deletados!');
                location.reload();
            } else {
                hideLoading();
                alert('Erro: ' + d.message);
            }
        })
        .catch(e => {
            hideLoading();
            console.error('Erro:', e);
            alert('Erro ao deletar grupos');
        });
    }
}

function selectAllGroups() {
    document.querySelectorAll('.group-checkbox').forEach(cb => {
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

document.addEventListener('DOMContentLoaded', function() {
    hideLoading();
});
