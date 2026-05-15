import React, {useCallback, useEffect, useState} from 'react';
import {useLazyQuery, useMutation} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Typography, Table} from '@jahia/moonstone';
import styles from './WorkflowTasksCleaner.scss';
import {RUN_CLEAN, WORKFLOW_LIST} from './WorkflowTasksCleaner.gql';

export const WorkflowTasksCleanerAdmin = () => {
    const {t} = useTranslation('workflow-tasks-cleaner');
    const [workflows, setWorkflows] = useState(null);
    const [runStatus, setRunStatus] = useState(null);

    useEffect(() => {
        document.title = `${t('label.title')} — Jahia Administration`;
    }, [t]);

    const [loadWorkflows, {loading: loadingWorkflows}] = useLazyQuery(WORKFLOW_LIST, {
        fetchPolicy: 'network-only',
        onCompleted: data => {
            setWorkflows(data?.workflowTasksCleanerWorkflowList || []);
        }
    });

    const [runClean, {loading: running}] = useMutation(RUN_CLEAN);

    const handleListWorkflows = useCallback(async () => {
        setWorkflows(null);
        loadWorkflows();
    }, [loadWorkflows]);

    const handleRunClean = useCallback(async () => {
        setRunStatus(null);
        try {
            const result = await runClean();
            if (result.data?.workflowTasksCleanerRunClean === true) {
                setRunStatus('success');
            } else {
                setRunStatus('error');
            }
        } catch {
            setRunStatus('error');
        }
    }, [runClean]);

    const listLiveMsg = loadingWorkflows ? t('label.loading') :
        (workflows !== null && workflows.length === 0) ? t('label.noWorkflows') : '';

    return (
        <div className={styles.wtc_container}>
            {/* Two fixed-role live regions — AT caches role at registration; dynamic mutation is ignored */}
            <div role="status" aria-live="polite" aria-atomic="true" className={styles.wtc_sr_only}>
                {runStatus === 'success' ? t('label.cleanSuccess') : ''}
            </div>
            <div role="alert" aria-live="assertive" aria-atomic="true" className={styles.wtc_sr_only}>
                {runStatus === 'error' ? t('label.error') : ''}
            </div>

            <div className={styles.wtc_header}>
                <h2>{t('label.title')}</h2>
            </div>

            <div className={styles.wtc_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            {runStatus === 'success' && (
                <div aria-hidden="true" className={`${styles.wtc_alert} ${styles['wtc_alert--success']}`}>
                    <span className={styles.wtc_alertIcon}>✓</span> {t('label.cleanSuccess')}
                </div>
            )}
            {runStatus === 'error' && (
                <div aria-hidden="true" className={`${styles.wtc_alert} ${styles['wtc_alert--error']}`}>
                    <span className={styles.wtc_alertIcon}>✕</span> {t('label.error')}
                </div>
            )}

            <div className={styles.wtc_section}>
                <h3>{t('label.cleanTitle')}</h3>
                <Typography>{t('label.cleanDescription')}</Typography>
                <div className={styles.wtc_actions}>
                    <Button
                        label={running ? t('label.running') : t('label.cleanButton')}
                        variant="primary"
                        isDisabled={running}
                        onClick={handleRunClean}
                    />
                </div>
            </div>

            <div className={styles.wtc_section}>
                <h3 id="wtc-workflow-table-heading">{t('label.workflowListTitle')}</h3>
                <Typography>{t('label.workflowListDescription')}</Typography>
                <div className={styles.wtc_actions}>
                    <Button
                        label={t('label.workflowListButton')}
                        variant="secondary"
                        isDisabled={loadingWorkflows}
                        onClick={handleListWorkflows}
                    />
                </div>

                {/* Persistent live region for list-loading status */}
                <div role="status" aria-live="polite" aria-atomic="true" className={styles.wtc_sr_only}>
                    {listLiveMsg}
                </div>

                {loadingWorkflows && (
                    <div className={styles.wtc_loading} aria-hidden="true">
                        <Loader size="big"/>
                    </div>
                )}

                {workflows && workflows.length === 0 && (
                    <Typography>{t('label.noWorkflows')}</Typography>
                )}

                {workflows && workflows.length > 0 && (
                    <div className={styles.wtc_table}>
                        <Table aria-labelledby="wtc-workflow-table-heading">
                            <thead>
                                <tr>
                                    <th scope="col">{t('label.tableId')}</th>
                                    <th scope="col">{t('label.tableName')}</th>
                                    <th scope="col">{t('label.tableType')}</th>
                                    <th scope="col">{t('label.tableTitle')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {workflows.map(w => (
                                    <tr key={w.id}>
                                        <td>{w.id}</td>
                                        <td>{w.name}</td>
                                        <td>{w.type}</td>
                                        <td>{w.title}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </Table>
                    </div>
                )}
            </div>
        </div>
    );
};

export default WorkflowTasksCleanerAdmin;
