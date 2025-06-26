package com.example.open_sourcepart2

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.open_sourcepart2.databinding.DialogAddExpenseBinding
import com.example.open_sourcepart2.databinding.FragmentExpensesBinding
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.*

class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var expenseAdapter: ExpenseAdapter

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    private var categories = listOf<Category>()
    private var selectedDate: Date = Calendar.getInstance().time

    private lateinit var pieChart: PieChart
    private lateinit var badge: ImageView
    private lateinit var levelText: TextView
    private lateinit var xpProgress: ProgressBar

    private var xp = 0
    private var level = 1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        databaseHelper = DatabaseHelper(requireContext())
        sessionManager = SessionManager(requireContext())

        pieChart = binding.pieChart
        badge = binding.congratsBadge
        //
        levelText = binding.levelText
        xpProgress = binding.xpProgress

        setupUI()
        loadData()
    }

    private fun setupUI() {
        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        expenseAdapter = ExpenseAdapter(emptyList())
        binding.rvExpenses.adapter = expenseAdapter

        binding.spinnerDate.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item,
            arrayOf("All Time", "Today", "This Week", "This Month", "This Year")
        )

        binding.fabAddExpense.setOnClickListener { showAddExpenseDialog() }
        binding.btnApplyFilters.setOnClickListener { applyFilters() }
    }

    private fun loadData() {
        val user = sessionManager.getUserDetails() ?: return
        categories = databaseHelper.getAllCategories(user.id)

        binding.spinnerCategory.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("All Categories") + categories.map { it.name }
        )

        val expenses = databaseHelper.getAllExpenses(user.id)
        if (expenses.isEmpty()) {
            binding.tvNoExpenses.visibility = View.VISIBLE
            binding.rvExpenses.visibility = View.GONE
        } else {
            binding.tvNoExpenses.visibility = View.GONE
            binding.rvExpenses.visibility = View.VISIBLE
            expenseAdapter.updateExpenses(expenses)
        }

        updateGamificationChart(expenses)
    }

    private fun updateGamificationChart(expenses: List<ExpenseWithCategory>) {
        pieChart.clear()
        val entries = ArrayList<PieEntry>()
        var totalExpense = 0f

        expenses.forEach {
            totalExpense += it.amount.toFloat()
            entries.add(PieEntry(it.amount.toFloat(), it.categoryName))
        }

        val dataSet = PieDataSet(entries, "Expenses").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
        }

        pieChart.data = PieData(dataSet)
        pieChart.invalidate()

        val maxBudget = 1000f
        if (totalExpense >= maxBudget) {
            badge.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "🎯 Max Budget Reached!", Toast.LENGTH_LONG).show()
            xp += 10
            if (xp >= 100) {
                level++
                xp -= 100
                Toast.makeText(requireContext(), "🎉 Leveled Up to $level!", Toast.LENGTH_SHORT).show()
            }
            levelText.text = "Level: $level (XP: $xp/100)"
            xpProgress.progress = xp
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun showAddExpenseDialog() {
        val dialogBinding = DialogAddExpenseBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        if (categories.isEmpty()) {
            Toast.makeText(requireContext(), "Add categories first", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            return
        }

        dialogBinding.spinnerCategory.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item,
            categories.map { it.name }
        )

        dialogBinding.btnSelectDate.text = "Date: ${displayDateFormat.format(selectedDate)}"
        dialogBinding.btnSelectDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate = Calendar.getInstance().apply {
                    set(y, m, d)
                }.time
                dialogBinding.btnSelectDate.text = "Date: ${displayDateFormat.format(selectedDate)}"
            }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH),
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSave.setOnClickListener {
            val amt = dialogBinding.etAmount.text.toString()
            val desc = dialogBinding.etDescription.text.toString()
            if (amt.isBlank() || desc.isBlank()) {
                Toast.makeText(requireContext(), "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val expense = Expense(
                amount = amt.toDouble(),
                description = desc,
                date = dateFormat.format(selectedDate),
                categoryId = categories[dialogBinding.spinnerCategory.selectedItemPosition].id,
                userId = sessionManager.getUserDetails()!!.id
            )

            val id = databaseHelper.addExpense(expense)
            if (id > 0) {
                Toast.makeText(requireContext(), "Added!", Toast.LENGTH_SHORT).show()
                loadData()
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Failed", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun applyFilters() {
        val all = databaseHelper.getAllExpenses(sessionManager.getUserDetails()!!.id)
        val filtered = all.filter {
            val categoryOk = binding.spinnerCategory.selectedItemPosition == 0 ||
                    it.categoryId == categories[binding.spinnerCategory.selectedItemPosition - 1].id

            val dateOk = when (binding.spinnerDate.selectedItemPosition) {
                1 -> it.date == dateFormat.format(Date())
                else -> true
            }
            categoryOk && dateOk
        }

        if (filtered.isEmpty()) {
            binding.tvNoExpenses.visibility = View.VISIBLE
            binding.rvExpenses.visibility = View.GONE
        } else {
            binding.tvNoExpenses.visibility = View.GONE
            binding.rvExpenses.visibility = View.VISIBLE
            expenseAdapter.updateExpenses(filtered)
        }

        updateGamificationChart(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
