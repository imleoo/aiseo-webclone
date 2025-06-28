document.addEventListener('DOMContentLoaded', function() {
    let currentTaskId = null;
    let pollingInterval = null;

    // 表单提交处理
    document.getElementById('cloneForm').addEventListener('submit', async function(e) {
        e.preventDefault();
        const submitBtn = document.getElementById('submitBtn');
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="loading me-2"></span>提交中...';

        try {
            const response = await fetch('/api/clone', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    url: document.getElementById('url').value,
                    threadCount: parseInt(document.getElementById('threadCount').value),
                    retryTimes: parseInt(document.getElementById('retryTimes').value),
                    sleepTime: parseInt(document.getElementById('sleepTime').value)
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            currentTaskId = data.taskId;
            updateTaskDetails(data);
            document.getElementById('taskDetails').style.display = 'block';
            startPolling();

        } catch (error) {
            alert('提交任务失败: ' + error.message);
        } finally {
            submitBtn.disabled = false;
            submitBtn.innerHTML = '开始克隆';
        }
    });

    // 刷新按钮处理 - 增强功能
    document.getElementById('refreshBtn').addEventListener('click', function() {
        if (currentTaskId) {
            fetchTaskStatus();
            const outputDir = document.getElementById('outputDir').textContent;
            if (outputDir) {
                loadFilesList(outputDir).catch(e => {
                    console.error('刷新文件列表失败:', e);
                    showAlert('刷新文件列表失败: ' + e.message, 'danger');
                });
            }
        }
    });

    // 显示通知的函数
    function showAlert(message, type = 'danger') {
        const alertDiv = document.createElement('div');
        alertDiv.className = `alert alert-${type} alert-dismissible fade show mt-2`;
        alertDiv.innerHTML = `
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        const container = document.getElementById('taskDetails');
        const existingAlert = container.querySelector('.alert');
        if (existingAlert) existingAlert.remove();
        container.appendChild(alertDiv);
    }

    // 轮询任务状态
    function startPolling() {
        if (pollingInterval) {
            clearInterval(pollingInterval);
        }
        pollingInterval = setInterval(fetchTaskStatus, 2000);
    }

    // 获取任务状态 - 增强错误处理
    async function fetchTaskStatus() {
        if (!currentTaskId) return;

        try {
            const response = await fetch(`/api/clone/${currentTaskId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            updateTaskDetails(data);

            // 如果任务完成或失败，停止轮询
            if (data.status === 'COMPLETED' || data.status === 'FAILED') {
                clearInterval(pollingInterval);
                if (data.status === 'COMPLETED' && data.outputDir) {
                    loadFilesList(data.outputDir).catch(e => {
                        console.error('加载文件列表失败:', e);
                        showAlert('加载文件列表失败: ' + e.message, 'warning');
                    });
                }
            }

        } catch (error) {
            console.error('获取任务状态失败:', error);
            clearInterval(pollingInterval);
            showAlert('获取任务状态失败: ' + error.message, 'danger');
        }
    }

    // 更新任务详情显示
    function updateTaskDetails(data) {
        document.getElementById('taskId').textContent = data.taskId;
        document.getElementById('taskUrl').textContent = data.url;
        document.getElementById('outputDir').textContent = data.outputDir;

        const statusElement = document.getElementById('status');
        statusElement.textContent = getStatusText(data.status);
        statusElement.className = 'badge ' + getStatusClass(data.status);

        document.getElementById('createdAt').textContent = formatDateTime(data.createdAt);
        document.getElementById('updatedAt').textContent = formatDateTime(data.updatedAt);
        document.getElementById('pagesCrawled').textContent = data.pagesCrawled;
        document.getElementById('filesDownloaded').textContent = data.filesDownloaded;

        const errorSection = document.getElementById('errorSection');
        if (data.errorMessage) {
            errorSection.style.display = 'block';
            document.getElementById('errorMessage').textContent = data.errorMessage;
        } else {
            errorSection.style.display = 'none';
        }
    }

    // 加载文件列表
    async function loadFilesList(directory) {
        try {
            const response = await fetch(`/api/files/list-details?directory=${encodeURIComponent(directory)}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const files = await response.json();
            const filesListElement = document.getElementById('filesList');
            filesListElement.innerHTML = '';

            // 按修改时间降序排序
            files.sort((a, b) => b.lastModified - a.lastModified);

            files.forEach(file => {
                const fileItem = document.createElement('div');
                fileItem.className = 'list-group-item';

                const fileLink = document.createElement('a');
                fileLink.href = `/api/files/view?filePath=${encodeURIComponent(file.path)}`;
                fileLink.className = 'text-decoration-none';
                fileLink.target = '_blank';
                fileLink.textContent = file.name;

                const fileDetails = document.createElement('div');
                fileDetails.className = 'small text-muted mt-1';
                
                const size = formatFileSize(file.size);
                const modified = formatDateTime(new Date(file.lastModified));
                fileDetails.textContent = `${size} - 修改时间: ${modified}`;

                fileItem.appendChild(fileLink);
                fileItem.appendChild(fileDetails);
                filesListElement.appendChild(fileItem);
            });

            document.getElementById('filesSection').style.display = 'block';

        } catch (error) {
            console.error('加载文件列表失败:', error);
        }
    }

    // 格式化文件大小
    function formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // 获取状态显示文本
    function getStatusText(status) {
        const statusMap = {
            'PENDING': '等待中',
            'RUNNING': '运行中',
            'COMPLETED': '已完成',
            'FAILED': '失败'
        };
        return statusMap[status] || status;
    }

    // 获取状态对应的样式类
    function getStatusClass(status) {
        const classMap = {
            'PENDING': 'bg-secondary',
            'RUNNING': 'bg-primary',
            'COMPLETED': 'bg-success',
            'FAILED': 'bg-danger'
        };
        return classMap[status] || 'bg-secondary';
    }

    // 格式化日期时间
    function formatDateTime(dateTimeStr) {
        if (!dateTimeStr) return '';
        const date = new Date(dateTimeStr);
        return date.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }
});