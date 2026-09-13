<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="12dp">

        <TextView
            android:id="@+id/txtProjectName"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textStyle="bold"
            android:textSize="17sp"
            android:text="پروژه" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="6dp"
            android:text="حداقل کلمه:" />

        <EditText
            android:id="@+id/edtMinLength"
            android:layout_width="44dp"
            android:layout_height="wrap_content"
            android:inputType="number"
            android:gravity="center"
            android:text="3" />

        <Button
            android:id="@+id/btnFindOverlap"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:text="پیدا کن" />
    </LinearLayout>

    <ProgressBar
        android:id="@+id/progressBarOverlap"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:layout_marginBottom="8dp"
        android:visibility="gone" />

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#CCCCCC" />

    <ScrollView
        android:id="@+id/scrollRoot"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp">

            <LinearLayout
                android:id="@+id/chatContainer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <View
                android:id="@+id/resultsDivider"
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#CCCCCC"
                android:layout_marginVertical="16dp"
                android:visibility="gone" />

            <TextView
                android:id="@+id/resultsHeading"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="نتیجه"
                android:textStyle="bold"
                android:textSize="18sp"
                android:paddingBottom="8dp"
                android:visibility="gone" />

            <LinearLayout
                android:id="@+id/resultsContainer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

        </LinearLayout>
    </ScrollView>

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#CCCCCC" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        android:gravity="bottom">

        <EditText
            android:id="@+id/edtPartInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:minLines="1"
            android:maxLines="6"
            android:hint="پارت بعدی رو اینجا بنویس یا بچسبون..." />

        <Button
            android:id="@+id/btnNextPart"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:text="بعدی" />
    </LinearLayout>

</LinearLayout>
