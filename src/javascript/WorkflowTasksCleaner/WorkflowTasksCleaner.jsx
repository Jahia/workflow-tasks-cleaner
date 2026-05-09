import React, {useCallback, useState} from 'react';
import {useLazyQuery, useMutation} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Typography, Table} from '@jahia/moonstone';
import styles from './WorkflowTasksCleaner.scss';
import {RUN_CLEAN, WORKFLOW_LIST} from './WorkflowTasksCleaner.gql';

export const WorkflowTasksCleanerAdmin = () => {
    const {t} = useTranslation('workflow-tasks-cleaner');
    const [workflows, setWorkflows] = useState(null);
    const [runStatus, setRunStatus] = useState(null);

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

    return (
        <div className={styles.wtc_container}>
            <div className={styles.wtc_header}>
                <h2>{t('label.title')}</h2>
            </div>

            <div className={styles.wtc_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            {runStatus === 'success' && (
                <div className={`${styles.wtc_alert} ${styles['wtc_alert--success']}`}>
                    {t('label.cleanSuccess')}
                </div>
            )}
            {runStatus === 'error' && (
                <div className={`${styles.wtc_alert} ${styles['wtc_alert--error']}`}>
                    {t('label.error')}
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
                <h3>{t('label.workflowListTitle')}</h3>
                <Typography>{t('label.workflowListDescription')}</Typography>
                <div className={styles.wtc_actions}>
                    <Button
                        label={t('label.workflowListButton')}
                        variant="secondary"
                        isDisabled={loadingWorkflows}
                        onClick={handleListWorkflows}
                    />
                </div>

                {loadingWorkflows && (
                    <div className={styles.wtc_loading}>
                        <Loader size="big"/>
                    </div>
                )}

                {workflows && workflows.length === 0 && (
                    <Typography>{t('label.noWorkflows')}</Typography>
                )}

                {workflows && workflows.length > 0 && (
                    <div className={styles.wtc_table}>
                        <Table>
                            <thead>
                                <tr>
                                    <th>{t('label.tableId')}</th>
                                    <th>{t('label.tableName')}</th>
                                    <th>{t('label.tableType')}</th>
                                    <th>{t('label.tableTitle')}</th>
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
