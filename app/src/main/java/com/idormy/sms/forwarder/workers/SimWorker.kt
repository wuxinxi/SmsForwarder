package com.idormy.sms.forwarder.workers

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.idormy.sms.forwarder.App
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.database.AppDatabase
import com.idormy.sms.forwarder.entity.MsgInfo
import com.idormy.sms.forwarder.entity.task.SimSetting
import com.idormy.sms.forwarder.entity.task.TaskSetting
import com.idormy.sms.forwarder.utils.PhoneUtils
import com.idormy.sms.forwarder.utils.TaskWorker
import com.idormy.sms.forwarder.utils.task.TaskUtils
import com.xuexiang.xutil.resource.ResUtils
import java.util.Date

@Suppress("PrivatePropertyName", "DEPRECATION")
class SimWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val TAG: String = SimWorker::class.java.simpleName

    override suspend fun doWork(): Result {
        val conditionType = inputData.getInt(TaskWorker.conditionType, -1)
        val simStateStr = inputData.getString(TaskWorker.msg)
        val taskList = AppDatabase.getInstance(App.context).taskDao().getByType(conditionType)
        for (task in taskList) {
            Log.d(TAG, "task = $task")

            // 根据任务信息执行相应操作
            val conditionList = Gson().fromJson(task.conditions, Array<TaskSetting>::class.java).toMutableList()
            if (conditionList.isEmpty()) {
                Log.d(TAG, "TASK-${task.id}：conditionList is empty")
                continue
            }
            val firstCondition = conditionList.firstOrNull()
            if (firstCondition == null) {
                Log.d(TAG, "TASK-${task.id}：firstCondition is null")
                continue
            }

            val simSetting = Gson().fromJson(firstCondition.setting, SimSetting::class.java)
            if (simSetting == null) {
                Log.d(TAG, "TASK-${task.id}：simSetting is null")
                continue
            }

            if (TaskUtils.simState != simSetting.simState) {
                Log.d(TAG, "TASK-${task.id}：networkState is not match, simSetting = $simSetting")
                continue
            }

            //TODO：判断其他条件是否满足，注意：SIM卡已准备就绪，延迟5秒才进入这里（给够搜索信号时间）
            /*if (!TaskUtils.isConditionMatch(task, msg)) {
                Log.d(TAG, "TASK-${task.id}：condition is not match")
                continue
            }*/

            val msg = StringBuilder()
            msg.append(String.format(ResUtils.getString(R.string.sim_state), simStateStr)).append("\n")
            if (TaskUtils.simState == TelephonyManager.SIM_STATE_READY) {
                // 获取 SIM 卡信息
                App.SimInfoList = PhoneUtils.getSimMultiInfo()
                //Log.d(TAG, App.SimInfoList.toString())
                App.SimInfoList.forEach {
                    msg.append("[SIM-").append(it.key + 1).append("]\n")
                    msg.append(ResUtils.getString(R.string.carrier_name)).append(": ").append(it.value.mCarrierName).append("\n")
                    //msg.append(getString(R.string.icc_id)).append(": ").append(it.value.mIccId).append("\n")
                    msg.append(ResUtils.getString(R.string.sim_slot_index)).append(": ").append(it.value.mSimSlotIndex).append("\n")
                    msg.append(ResUtils.getString(R.string.number)).append(": ").append(it.value.mNumber).append("\n")
                    msg.append(ResUtils.getString(R.string.country_iso)).append(": ").append(it.value.mCountryIso).append("\n")
                    msg.append(ResUtils.getString(R.string.subscription_id)).append(": ").append(it.value.mSubscriptionId).append("\n")
                }
            }

            //TODO: 组装消息体 && 执行具体任务
            val msgInfo = MsgInfo("task", task.name, msg.toString().trimEnd(), Date(), task.description)
            val actionData = Data.Builder().putLong(TaskWorker.taskId, task.id).putString(TaskWorker.taskActions, task.actions).putString(TaskWorker.msgInfo, Gson().toJson(msgInfo)).build()
            val actionRequest = OneTimeWorkRequestBuilder<ActionWorker>().setInputData(actionData).build()
            WorkManager.getInstance().enqueue(actionRequest)
        }

        return Result.success()

    }

}