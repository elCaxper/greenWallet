package com.example.greenwallet

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.greenwallet.databinding.FragmentSecondBinding
import com.google.zxing.integration.android.IntentIntegrator

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initScanner();

        binding.buttonSecond.setOnClickListener {
//            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
            var sendAddress: String ="" // "0x6f95CE590872129aee367AC4EBcaFeB89F8090b2"
            var sendAmount: String = "" // "0.0001"

            var words = binding.lbQr.text.split(";")
            for (s in words) {
                if(s.isEmpty()) {
                    return@setOnClickListener
                } else {
                    if (s.startsWith("0x"))
                        sendAddress = s

                    if(s.contains("."))
                        sendAmount=s
                }
            }


            //if (sendAddress.isEmpty() || sendAmount.isEmpty())
            //    return@setOnClickListener

            (activity as SendBalance?)
                ?.sendValueAddress()

        }
    }

    private fun initScanner(){
        val integrator = IntentIntegrator(activity)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan transaction Qr")
//        integrator.setTorchEnabled(true)
        integrator.setBeepEnabled(true)
        integrator.initiateScan()
    }


    public fun my_func(read: String) {
        val lb_result = binding.lbQr as TextView
        lb_result.setText("Resultado: " + read)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}