import React, {useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Input, Tooltip, Typography} from '@jahia/moonstone';
import styles from './WorkflowTasksCleaner.scss';
import {GET_CONFIG, SAVE_CONFIG} from './WorkflowTasksCleaner.gql';

const DEFAULT_CONFIG = {
    cronExpression: '0 30 2 * * ?'
};

export const SchedulerConfig = () => {
    const {t} = useTranslation('workflow-tasks-cleaner');
    const [form, setForm] = useState(DEFAULT_CONFIG);
    const [saveStatus, setSaveStatus] = useState(null);
    const liveRef = useRef(null);

    useEffect(() => {
        document.title = `${t('label.menu_configuration')} — Jahia Administration`;
    }, [t]);

    const {data, loading} = useQuery(GET_CONFIG, {fetchPolicy: 'network-only'});

    useEffect(() => {
        if (data?.workflowTasksCleanerConfig) {
            setForm({
                cronExpression: data.workflowTasksCleanerConfig.cronExpression
            });
        }
    }, [data]);

    const [saveConfig, {loading: saving}] = useMutation(SAVE_CONFIG);

    const handleChange = (field, value) => {
        setForm(prev => ({...prev, [field]: value}));
    };

    const handleSave = async () => {
        setSaveStatus(null);
        try {
            const result = await saveConfig({
                variables: {
                    cronExpression: form.cronExpression
                }
            });
            setSaveStatus(result.data?.workflowTasksCleanerSaveConfig === true ? 'success' : 'error');
        } catch {
            setSaveStatus('error');
        }

        setTimeout(() => liveRef.current?.focus(), 50);
    };

    const saveLiveMsg = saveStatus === 'success' ? t('label.scheduler.saved') :
        saveStatus === 'error' ? t('label.error') : '';

    return (
        <div className={styles.wtc_container}>
            {/* Persistent live region for save status — always in DOM so AT registers it */}
            <div
                ref={liveRef}
                tabIndex={-1}
                role={saveStatus === 'error' ? 'alert' : 'status'}
                aria-live={saveStatus === 'error' ? 'assertive' : 'polite'}
                aria-atomic="true"
                className={styles.wtc_sr_only}
            >
                {saveLiveMsg}
            </div>

            <div className={styles.wtc_header}>
                <h2>{t('label.menu_configuration')}</h2>
            </div>

            <div className={styles.wtc_description}>
                <Typography>{t('label.scheduler.description')}</Typography>
            </div>

            {saveStatus === 'success' && (
                <div aria-hidden="true" className={`${styles.wtc_alert} ${styles['wtc_alert--success']}`}>
                    <span className={styles.wtc_alertIcon}>✓</span> {t('label.scheduler.saved')}
                </div>
            )}
            {saveStatus === 'error' && (
                <div aria-hidden="true" className={`${styles.wtc_alert} ${styles['wtc_alert--error']}`}>
                    <span className={styles.wtc_alertIcon}>✕</span> {t('label.error')}
                </div>
            )}

            <div className={styles.wtc_form}>
                <div className={styles.wtc_fieldGroup}>
                    {/* Tooltip trigger moved outside <label> — interactive elements must not be children of <label> */}
                    <div className={styles.wtc_label}>
                        <label htmlFor="wtc-cron">
                            {t('label.scheduler.cronExpression')}
                        </label>
                        <Tooltip label={t('label.scheduler.cronExpressionTooltip')}>
                            <button
                                type="button"
                                aria-label={t('label.scheduler.cronExpressionInfo')}
                                className={styles.wtc_tooltip}
                            >
                                ⓘ
                            </button>
                        </Tooltip>
                    </div>
                    <Input
                        id="wtc-cron"
                        aria-describedby="wtc-cron-desc"
                        className={styles.wtc_inputWide}
                        value={loading ? '' : form.cronExpression}
                        isDisabled={loading}
                        autoComplete="off"
                        onChange={e => handleChange('cronExpression', e.target.value)}
                    />
                    <span id="wtc-cron-desc" className={styles.wtc_sr_only}>
                        {t('label.scheduler.cronExpressionTooltip')}
                    </span>
                </div>
            </div>

            <div className={styles.wtc_restartHint}>
                <Typography variant="caption">{t('label.scheduler.restartHint')}</Typography>
            </div>

            <div className={styles.wtc_actions}>
                <Button
                    label={t('label.scheduler.save')}
                    variant="primary"
                    isDisabled={loading || saving}
                    onClick={handleSave}
                />
            </div>
        </div>
    );
};

export default SchedulerConfig;
