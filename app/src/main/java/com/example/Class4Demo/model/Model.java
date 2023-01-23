package com.example.Class4Demo.model;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.os.HandlerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Model {
    private static final Model _instance = new Model();


    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    private final FirebaseModel firebaseModel = new FirebaseModel();
    AppLocalDbRepository localDb = AppLocalDb.getAppDb();

    public static Model instance() {
        return _instance;
    }

    private Model() {
    }


    public interface Listener<T> {
        void onComplete(T data);
    }


    public enum LoadingState {
        LOADING,
        NOT_LOADING
    }

    final public MutableLiveData<LoadingState> EventStudentsListLoadingState =
            new MutableLiveData<>(LoadingState.NOT_LOADING);


    private LiveData<List<Student>> studentList;

    public LiveData<List<Student>> getAllStudents() {
        if (studentList == null) {
            studentList = localDb.studentDao().getAll();
        }
        return studentList;
    }

    public void refreshAllStudents() {
        EventStudentsListLoadingState.setValue(LoadingState.LOADING);
        //get local last update
        Long localLastUpdate = Student.getLocalLastUpdate();

        //get all updated records from firebase since last local update
        firebaseModel.getAllStudentsSince(localLastUpdate, list -> {
            executor.execute(() -> {
                Log.d("TAG", "firebase returned: " + list.size());
                Long time = localLastUpdate;
                for (Student st : list) {
                    //insert new records into ROOM
                    localDb.studentDao().insertAll(st);
                    if (time < st.getLastUpdated())
                        time = st.getLastUpdated();
                }
                //update last local update
                Student.setLocalLastUpdate(time);
                EventStudentsListLoadingState.postValue(LoadingState.NOT_LOADING);
            });
        });
    }

    public void createStudent(String email, String password, Listener<String> callback) {
        firebaseModel.createStudent(email, password, callback);
    }


    public void getStudentById(String id, Listener<Student> callback) {
//        firebaseModel.getStudentById(id, callback);
        executor.execute(() -> {
            Student st = localDb.studentDao().getStudentById(id);
            Log.d("TAG", "getStudentById: " + st.getName());
            mainHandler.post(() -> callback.onComplete(st));
        });
    }


    public void addStudent(Student st, Listener<Void> listener) {
        firebaseModel.addStudent(st, (Void) -> {
            refreshAllStudents();
            listener.onComplete(null);
        });

    }


    public void uploadImage(String name, Bitmap bitmap, Listener<String> listener) {
        firebaseModel.uploadImage(name, bitmap, listener);
    }
}
